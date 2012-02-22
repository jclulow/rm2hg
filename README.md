# rm2hg (Redmine/Mercurial-Server Integration)

## Overview

We use mercurial-server for access control to hg repositories via
ssh, and redmine for issue tracking and user management.  This code
will:

  * Get a list of users with the "Committer" role in a project and
     grant them write access to the repository with the same
     name as the project.

  * Get a list of SSH keys stored in a custom value against each
     user in redmine and populate the mercurial-server key
     directory appropriately.

  * Create repositories for which there are users with write access
     that do not exist already in the filesystem.

  * Run the mercurial-server command that refreshes
      ~hg/.authorized\_keys

The program is built to pull its information directly from the
redmine database (which we keep in PostgreSQL) and to do so out
of cron every couple of minutes.

## Build and Configuration

You'll need the Scala Simple Build Tool.  This is easier than it sounds:

```bash
mkdir "$HOME/.sbt" &&
wget -O "$HOME/.sbt/sbt-launch.jar" "http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/0.11.0/sbt-launch.jar" &&
cat >"$HOME/bin/sbt" <<'EOF'
#!/bin/ksh
 
exec java \
        -Dfile.encoding=UTF8 \
        -Xmx1536M \
        -Xss1M \
        -XX:+CMSClassUnloadingEnabled \
        -XX:MaxPermSize=256m \
        -Dsbt.boot.directory=$HOME/.sbt/boot \
        -jar $HOME/.sbt/sbt-launch.jar \
        "$@"
EOF
chmod +x "$HOME/bin/sbt"
```

Then, build the giant JAR that contains everything.  This JAR will be
something like `target/scala-2.9.0/rm2hg\_2.9.0-1.0-SNAPSHOT-one-jar.jar`

```bash
sbt one-jar
```

Configuration goes in `/etc/opt/rm2hg.json` and should have the same
keys as `sample\_configuration.json` in this repo.  The actual
values and the rest of the configuration depend on how you do
mercurial-server at your site.

## License

ISC.
