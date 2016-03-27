# oriana
Oriana binds slick and akka together in an elegant way

## Features

This libray provides two simple features

### Initialization

When deploying an application with a NoSQL persistence system, it is rarely necessary to explicitly initialize
the datastore. Usually you simply configure the backend and get to work. With an SQL database, there are tables
to be crated, potentially updates to be performed and generally a degree of housekeeping required before the application
itself can take control. Oriana is intended to aid in that housekeeping by delaying database access until all
initialization tasks are complete.

Oriana ships with a simple initializer that iterates all tables and issues a create statement if they are found
to be missing (access results in an error). More elaborate schemes  (e.g. Liquibase support) can easily be
build on top of this base function. Creating your schema becomes as easy as the following fragment

    val myDB = system.actorOf(DatabaseActor.props(new MyContext(config)), "database")
    myDB ! Init

### Access and ease of use

Access persistent data is often inconvenient. Actors need to be looked up, global objects need to be queried
and failing operations need to be retried (especially when using optimistic locking). Oriana aims to reduce the
notational overhead of these tasks by offering automatic retry support for transactions, and pseudo-operators producing
a database access context. 

    executeDBOperation { ctx: MyContext =>
        import ctx.api._
        ctx.database.run(context.unicornTable.query.result.head)
    }
    
The above simple fragment will return the first element of the unicornTable (probably a `TableAccess` instance), without
requiring intricate knowledge about the specifics of scheduling and initialization.
 
    executeDBTransaction { ctx: MyContext =>
        import ctx.api._
        ctx.unicornTable.query.result.headOption flatMap {
            case Some(pony) => DBIO.successful(pony)
            case None => ctx.unicornTable.query += DefaultUnicorn map (_ => DefaultUnicorn)
        }
    }
    
The above fragement likewise queries for the first unicorn, but if none is found, instead inserts a default value
before returning it. This is executed transactionally, and any failures encountered within the block will
cause a delay and retry with a given schedule.

### Flows and Streams, oh my!

Oriana integrates the database in all levels of Akka streams. Thus, it becomes easy, even natual to think of the
database as a `Source` or as a `Sink` for some stream of data. Particularly in bulk import / export scenarios, this
leads to very natural handling of data acquisition, transformation and storage. The build-in backpressure of akka streams
allows to easily handle large datasets without danger of overloading your application or database.