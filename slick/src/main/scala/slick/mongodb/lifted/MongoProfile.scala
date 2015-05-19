package slick.mongodb.lifted

import slick.backend.RelationalBackend
import slick.dbio.Effect.Schema
import slick.dbio.{SynchronousDatabaseAction, Effect, NoStream}
import slick.memory.DistributedBackend
import slick.util.DumpInfo

import scala.language.{higherKinds, implicitConversions}
import slick.ast._
import slick.compiler.QueryCompiler
import slick.lifted.Query
import slick.mongodb.direct.MongoBackend
import slick.profile.{FixedBasicAction, RelationalDriver, RelationalProfile}

// TODO: split into traits?
trait MongoProfile extends RelationalProfile with MongoInsertInvokerComponent with MongoTypesComponent{ driver: MongoDriver =>

   type Backend = MongoBackend
   val backend = MongoBackend

  protected trait CommonImplicits extends super.CommonImplicits with ImplicitColumnTypes
//  trait Implicits extends super.Implicits with CommonImplicits
//  trait SimpleQL extends super.SimpleQL with Implicits
  trait API extends super.API with CommonImplicits

  override val Implicit: Implicits = simple
  override val simple: SimpleQL  = new SimpleQL {}
  override val api = new API{}


  type QueryActionExtensionMethods[R, S <: NoStream] = QueryActionExtensionMethodsImpl[R, S]
  type StreamingQueryActionExtensionMethods[R, T] = StreamingQueryActionExtensionMethodsImpl[R, T]
  type SchemaActionExtensionMethods = SchemaActionExtensionMethodsImpl
  type InsertActionExtensionMethods[T] = InsertActionExtensionMethodsImpl[T]

  def createQueryActionExtensionMethods[R, S <: NoStream](tree: Node, param: Any): QueryActionExtensionMethods[R, S] =
    ???
  def createStreamingQueryActionExtensionMethods[R, T](tree: Node, param: Any): StreamingQueryActionExtensionMethods[R, T] =
    ???
  def createSchemaActionExtensionMethods(schema: SchemaDescription): SchemaActionExtensionMethods =
    ???


 

  // TODO: extend for complicated node structure, probably mongodb nodes should be used
  /** (Partially) compile an AST for insert operations */
  override def compileInsert(n: Node): CompiledInsert = n

  /** The compiler used for queries */
  override def queryCompiler: QueryCompiler = compiler ++ QueryCompiler.relationalPhases
  /** The compiler used for updates */
  override def updateCompiler: QueryCompiler = ???
  /** The compiler used for deleting data */
  override def deleteCompiler: QueryCompiler = ???
  /** The compiler used for inserting data */
  override def insertCompiler: QueryCompiler = ???

  trait ImplicitColumnTypes extends super.ImplicitColumnTypes{
    override implicit def charColumnType: BaseColumnType[Char] = ScalaBaseType.charType
    override implicit def longColumnType: BaseColumnType[Long] with NumericTypedType = ScalaBaseType.longType
    override implicit def byteColumnType: BaseColumnType[Byte] with NumericTypedType = ScalaBaseType.byteType
    override implicit def intColumnType: BaseColumnType[Int] with NumericTypedType = ScalaBaseType.intType
    override implicit def booleanColumnType: BaseColumnType[Boolean] = ScalaBaseType.booleanType
    override implicit def shortColumnType: BaseColumnType[Short] with NumericTypedType = ScalaBaseType.shortType
    override implicit def doubleColumnType: BaseColumnType[Double] with NumericTypedType = ScalaBaseType.doubleType
    override implicit def bigDecimalColumnType: BaseColumnType[BigDecimal] with NumericTypedType = ScalaBaseType.bigDecimalType
    override implicit def floatColumnType: BaseColumnType[Float] with NumericTypedType = ScalaBaseType.floatType
    override implicit def stringColumnType: BaseColumnType[String] = ScalaBaseType.stringType
  }

  trait Implicits extends super.Implicits with ImplicitColumnTypes{
    override implicit def ddlToDDLInvoker(d: SchemaDescription): DDLInvoker = createDDLInvoker(d)
    implicit def queryToLiftedMongoInvoker[T,C[_]](q: Query[_,T,C])(implicit session: MongoBackend#Session): LiftedMongoInvoker[T] =
      new LiftedMongoInvoker[T](queryCompiler.run(q.toNode).tree,session)
  }

  trait SimpleQL extends super.SimpleQL with Implicits

  // TODO: not required for MongoDB:
  /** Create a DDLInvoker -- this method should be implemented by drivers as needed */
  override def createDDLInvoker(ddl: SchemaDescription): DDLInvoker = throw new UnsupportedOperationException("Mongo driver doesn't support ddl operations.")

  override type SchemaDescription = SchemaDescriptionDef
  override def buildSequenceSchemaDescription(seq: Sequence[_]): SchemaDescription = ???
  override def buildTableSchemaDescription(table: Table[_]): SchemaDescription = ???

  override type QueryExecutor[T] = QueryExecutorDef[T]
  //override type UnshapedQueryExecutor[T] = UnshapedQueryExecutorDef[T]
  /** Create an executor -- this method should be implemented by drivers as needed */
  override def createQueryExecutor[R](tree: Node, param: Any): QueryExecutor[R] = ???
  //override def createUnshapedQueryExecutor[M](value: M): UnshapedQueryExecutor[M] = ???



  def createInsertActionExtensionMethods[T](compiled: MongoDriver.CompiledInsert) = ???

  type DriverAction = this.type
  type StreamingDriverAction = this.type
}

// TODO: make it a class?
trait MongoDriver extends MongoProfile with RelationalDriver {
  override val profile: MongoProfile = this
}
object MongoDriver extends MongoDriver
