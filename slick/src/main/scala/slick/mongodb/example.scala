package slick.mongodb


import slick.mongodb.direct.MongoBackend
import slick.mongodb.lifted.MongoDriver.api._

import scala.slick.driver


object example extends App  {
    // The query interface for the Suppliers table
    val suppliers: TableQuery[Suppliers] = TableQuery[Suppliers]

    // the query interface for the Coffees table
    val coffees: TableQuery[Coffees] = TableQuery[Coffees]

    // Create a connection (called a "session") to an in-memory H2 database
    val db = Database.forURL("jdbc:h2:mem:hello", driver = "org.h2.Driver")
    db.withSession { implicit session =>

      // Create the schema by combining the DDLs for the Suppliers and Coffees
      // tables using the query interfaces
      (suppliers.ddl ++ coffees.ddl).create


      /* Create / Insert */

      // Insert some suppliers
      suppliers += (101, "Acme, Inc.", "99 Market Street", "Groundsville", "CA", "95199")
      suppliers += ( 49, "Superior Coffee", "1 Party Place", "Mendocino", "CA", "95460")
      suppliers += (150, "The High Ground", "100 Coffee Lane", "Meadows", "CA", "93966")

      // Insert some coffees (using JDBC's batch insert feature)
      val coffeesInsertResult: Option[Int] = coffees ++= Seq (
        ("Colombian",         101, 7.99, 0, 0),
        ("French_Roast",       49, 8.99, 0, 0),
        ("Espresso",          150, 9.99, 0, 0),
        ("Colombian_Decaf",   101, 8.99, 0, 0),
        ("French_Roast_Decaf", 49, 9.99, 0, 0)
      )

      val allSuppliers: List[(Int, String, String, String, String, String)] =
        suppliers.list





  }

