package slick.mongodb.lifted

import com.mongodb.casbah.commons.MongoDBObject
import slick.SlickException
import slick.ast.TypeUtil.:@
import slick.backend.RelationalBackend
import slick.dbio.Effect.Schema
import slick.dbio.{Streaming, SynchronousDatabaseAction, Effect, NoStream}
import slick.jdbc.StreamingInvokerAction
import slick.memory.DistributedBackend
import slick.relational.CompiledMapping
import slick.util.{SQLBuilder, DumpInfo}


import scala.language.{higherKinds, implicitConversions}
import slick.ast._
import slick.compiler.QueryCompiler
import slick.lifted.Query
import slick.mongodb.direct.{MongoQuery, MongoBackend}
import slick.profile.{FixedBasicStreamingAction, FixedBasicAction, RelationalDriver, RelationalProfile}

// TODO: split into traits?
trait MongoProfile extends RelationalProfile with MongoInsertInvokerComponent with MongoTypesComponent{ driver: MongoDriver =>

   type Backend = MongoBackend
   val backend = MongoBackend
  override val Implicit: Implicits = simple
  override val simple: SimpleQL  = new SimpleQL {}
  override val api = new API{}



  protected trait CommonImplicits extends super.CommonImplicits with ImplicitColumnTypes
//  trait Implicits extends super.Implicits with CommonImplicits
//  trait SimpleQL extends super.SimpleQL with Implicits
  trait API extends super.API with CommonImplicits// todo maybe I should use methods from simple in api
{
  implicit def queryToLiftedMongoInvoker[T,C[_]](q: Query[_,T,C])(implicit session: MongoBackend#Session): LiftedMongoInvoker[T] =
    new LiftedMongoInvoker[T](queryCompiler.run(q.toNode).tree,session)
}
  trait SimpleQL extends super.SimpleQL with Implicits




  type SchemaActionExtensionMethods = SchemaActionExtensionMethodsImpl
  type DriverAction[+R, +S <: NoStream, -E <: Effect] = FixedBasicAction[R, S, E] //todo not sure if it is right atcion
  type StreamingDriverAction[+R, +T, -E <: Effect] = FixedBasicStreamingAction[R, T, E]


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

  ///////////////////////////////////////////StreamingQueryActionExtension///////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////
  type StreamingQueryActionExtensionMethods[R, T] = StreamingQueryActionExtensionMethodsImpl[R, T]

  def createStreamingQueryActionExtensionMethods[R, T](tree: Node, param: Any): StreamingQueryActionExtensionMethods[R, T] =
    new StreamingQueryActionExtensionMethodsImpl[R,T](tree,param)

  class StreamingQueryActionExtensionMethodsImpl[R, T](tree: Node, param: Any) extends QueryActionExtensionMethodsImpl[R, Streaming[T]](tree, param) with super.StreamingQueryActionExtensionMethodsImpl[R, T] {
    override def result: StreamingDriverAction[R, T, Effect.Read] = super.result.asInstanceOf[StreamingDriverAction[R, T, Effect.Read]]
  }


///////////////////////////////////////////QueryActionExtension///////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////
type QueryActionExtensionMethods[R, S <: NoStream] = QueryActionExtensionMethodsImpl[R, S]

  def createQueryActionExtensionMethods[R, S <: NoStream](tree: Node, param: Any): QueryActionExtensionMethods[R, S] =
    new QueryActionExtensionMethodsImpl[R,S](tree,param)


  class QueryActionExtensionMethodsImpl[R, S <: NoStream](tree: Node, param: Any) extends super.QueryActionExtensionMethodsImpl[R, S] {
    /** An Action that runs this query. */
    def result = ???
  }

  ////////////////////////////////////////////InsertActionExtension///////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  type InsertActionExtensionMethods[T] = InsertActionExtensionMethodsImpl[T]

  def createInsertActionExtensionMethods[T](compiled: MongoDriver.CompiledInsert) = new InsertActionExtensionMethodsImpl[T](compiled)

  class InsertActionExtensionMethodsImpl[T](compiled: CompiledInsert) extends super.InsertActionExtensionMethodsImpl[T] {
    protected[this] def wrapAction[E <: Effect, T](name: String, f: Backend#Session => Any): DriverAction[T, NoStream, E] =
      new SynchronousDatabaseAction[T, NoStream, Backend, E] with DriverAction[T, NoStream, E] {
        def run(ctx: Backend#Context) = f(ctx.session).asInstanceOf[T]
        override def getDumpInfo = ??? //super.getDumpInfo.copy(name = name)
        def overrideStatements(_statements: Iterable[String]) =
          throw new SlickException("overrideStatements is not supported for insert operations")
      }

    val inv = createInsertInvoker[T](compiled)
    type SingleInsertResult = Unit
    type MultiInsertResult = Unit
    def += (value: T) = wrapAction("+=",inv.+=(value)(_))
    def ++= (values: Iterable[T]) = wrapAction("+=",inv.++=(values)(_))
  }


/////////////////////////////////////////////////QueryExecutor//////////////////////////////////////////
  /** Create an executor -- this method should be implemented by drivers as needed */
  override type QueryExecutor[T] =  QueryExecutorDef[T]
  override def createQueryExecutor[R](tree: Node, param: Any): QueryExecutor[R] = ???

  ///////////////////////////////////////////////////////////////////////////////////////


  // TODO: not required for MongoDB:
  /** Create a DDLInvoker -- this method should be implemented by drivers as needed */
  override def createDDLInvoker(ddl: SchemaDescription): DDLInvoker = throw new UnsupportedOperationException("Mongo driver doesn't support ddl operations.")

  override type SchemaDescription = SchemaDescriptionDef
  override def buildSequenceSchemaDescription(seq: Sequence[_]): SchemaDescription = ???
  override def buildTableSchemaDescription(table: Table[_]): SchemaDescription = ???
  def createSchemaActionExtensionMethods(schema: SchemaDescription): SchemaActionExtensionMethods =
    ???
  class SchemaActionExtensionMethodsImpl(schema: SchemaDescription) extends super.SchemaActionExtensionMethodsImpl {
    def create = ???
    def drop = ???
  }

  //todo methods will be removed
//  override type UnshapedQueryExecutor[T] = UnshapedQueryExecutorDef[T]
//  override def createUnshapedQueryExecutor[M](value: M): UnshapedQueryExecutor[M] = ???

}

// TODO: make it a class?
trait MongoDriver extends MongoProfile with RelationalDriver

object MongoDriver extends MongoDriver
