/*
 * rm2hg
 *   - Redmine to Mercurial-Server Integration for illumos.org
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Copyright 2012 Joshua M. Clulow <josh@sysmgr.org>
 *
 */

package org.sysmgr.rm2hg

import java.sql._
import java.io.File
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.node.ObjectNode
import org.codehaus.jackson.map.ObjectMapper
import scala.sys.process.Process


class Config(file: File) {

    private val m = new ObjectMapper()
    private val n = m.readValue(file, classOf[JsonNode])

    def c(key: String) = {
        n.path(key).getTextValue
    }

    def c(keyPath: String*) = {
        try {
            def recurs(rem: List[String], nod: JsonNode): String = {
                if (rem.isEmpty)
                    nod.getTextValue
                else
                    recurs(rem.tail, nod.path(rem.head))
            }
            recurs(keyPath.toList, n)
        } catch {
            case ex: Exception => throw new RuntimeException("Could not find " +
                "config value for " + keyPath.mkString("."), ex)
        }
    }
}

case class Predicate(val login: String, val repository: String)
case class SshKey(val login: String, val sshKey: String)

class RedmineDatabase(config: Config) {

    Class.forName("org.postgresql.Driver")
    private val con = DriverManager.getConnection(
        config.c("db", "url"),
        config.c("db", "username"),
        config.c("db", "password")
    )

    private val psPredicates = con.prepareStatement("""
        SELECT
            p.identifier AS REPO_NAME,
            u.login AS LOGIN_NAME
        FROM
            roles r
            INNER JOIN member_roles mr ON (r.id = mr.role_id)
            INNER JOIN members m ON (m.id = mr.member_id)
            INNER JOIN projects p ON (p.id = m.project_id)
            INNER JOIN users u ON (u.id = m.user_id)
            WHERE
            r.name = 'Committer'
        ORDER BY
            REPO_NAME, LOGIN_NAME;
    """)

    private val psSshKeys = con.prepareStatement("""
        SELECT
            u.login AS LOGIN_NAME,
            cv.value AS SSH_KEY
        FROM
            custom_values cv
            INNER JOIN custom_fields cf ON (cf.id = cv.custom_field_id)
            INNER JOIN users u ON (cv.customized_type = 'Principal' AND u.id = cv.customized_id)
        WHERE
            cf.name = 'SSH Key' AND
            cv.value IS NOT NULL AND
            LENGTH(cv.value) > 10
        ORDER BY
            LOGIN_NAME;
    """)

    def predicates = {
        val x = new collection.mutable.ListBuffer[Predicate]()
        val rs = psPredicates.executeQuery()
        while (rs.next()) {
            x += new Predicate(rs.getString("LOGIN_NAME").trim, rs.getString("REPO_NAME").trim)
        }
        rs.close()
        x.toList
    }

    def sshKeys = {
        val x = new collection.mutable.ListBuffer[SshKey]()
        val rs = psSshKeys.executeQuery()
        while (rs.next()) {
            val sk = rs.getString("SSH_KEY").trim.lines.map { x: String => x.trim }.mkString
            val ln = rs.getString("LOGIN_NAME")
            x += new SshKey(ln, sk)
        }
        rs.close()
        x.toList
    }

    def close() {
        con.close()
    }
}

object Main extends App {

    val confFilePath = if (args.length > 0)
        args.first
    else
        "/etc/opt/rm2hg.json"

    /* initialise from JSON config file */
    val c = new Config(new File(confFilePath))
    val fileKeyDir = new File(c.c("hg", "key_dir"))
    val fileAccessConf = new File(c.c("hg", "access_conf"))

    def runHg(args: String*) {
        val aa = c.c("hg", "command") :: args.toList
        val hg = Process(aa)
        hg.run().exitValue()
    }

    def withOutputFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
        val p = new java.io.PrintWriter(f)
        try { op(p) } finally { p.close() }
    }
    
    val reSanitary = """^([a-zA-Z0-9_-]+)$""" r

    /* get data from database */
    val rmdb = new RedmineDatabase(c)
    val keys = rmdb.sshKeys
    val preds = rmdb.predicates
    val repos = new collection.mutable.HashSet[String]()
    rmdb.close()

    /* for each user->repo mapping, write an appropriate access
     * predicate into our access.conf for mercurial server */
    withOutputFile(fileAccessConf) { out =>
        preds.foreach {
            _ match {
              case Predicate(reSanitary(login), reSanitary(repo)) =>
                  out.println("write repo=" + repo + " user=users/" + login)
                  repos += repo
              case _ => /* unsanitary input */
            }
        }
    }

    /* for each SSH key we have, create a file with user's name
     * in the specified directory */
    keys.foreach {
        _ match {
            case SshKey(reSanitary(login), key) =>
                withOutputFile(new File(fileKeyDir, login)) { out =>
                    out.println(key)
                }
            case _ => /* unsanitary input */
        }
    }

    /* for the list of projects with committers, create
     * a repository if none exists already: */
    repos.toList.sorted.foreach { r =>
        val f = new File(c.c("hg", "repo_dir"), r)
        if (!f.exists()) {
            println("Creating repository: " + f.getCanonicalPath)
            runHg("init", f.getCanonicalPath)
        }
    }

}

