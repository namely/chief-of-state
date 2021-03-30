# Migrating from 0.7 to 0.8+

COS v0.8 upgrades to the latest akka persistence libraries (>5.0.0), which change the journal, snapshot, and tags representations in the db (see the [docs](https://doc.akka.io/docs/akka-persistence-jdbc/5.0.0/migration.html) for more info). COS v0.8 provides a new built-in migrator to handle this upgrade automatically on boot. If you are starting a new application OR are already running on a COS version >= 0.8, you may ignore these instructions.

### Instructions
If you are currently running COS v0.7, please initialize the internal "schema version" to `0` by setting the following environment variable:
```
COS_MIGRATIONS_INITIAL_VERSION: 0
```

Then, boot a single instance of chief-of-state v0.8 or higher. If you have scaled out COS to more than 1 node, it's currently important to temporarily scale down to a single node, as the schema migrator runs on node start. (There is an existing feature request [#269](https://github.com/namely/chief-of-state/issues/269) to implement the migrator inside of a [cluster singleton](https://doc.akka.io/docs/akka/current/typed/cluster-singleton.html), and these docs will be amended when that is complete).

After the node starts and is able to answer requests, you may scale out again to the desired number of nodes.
