package dotty.tools.dotc
package transform

import TreeTransforms._
import core.DenotTransformers._
import core.Symbols._
import core.Contexts._
import core.Types._
import core.Flags._
import core.Decorators._
import core.SymDenotations._
import core.StdNames.nme
import core.Names._
import core.NameOps._
import ast.Trees._
import SymUtils._
import dotty.tools.dotc.ast.tpd
import collection.{ mutable, immutable }
import collection.mutable.{ LinkedHashMap, LinkedHashSet, TreeSet }

/**
 *  Rewires closures to implement more specific types of Functions.
 */
class FunctionalInterfaces extends MiniPhaseTransform {
  import tpd._

  def phaseName: String = "functionalInterfaces"

  var allowedReturnTypes: Set[Symbol] = _ // moved here to make it explicit what specializations are generated
  var allowedArgumentTypes: Set[Symbol] = _
  val maxArgsCount = 2

  def shouldSpecialize(m: MethodType)(implicit ctx: Context) =
    (m.paramTypes.size <= maxArgsCount) &&
      m.paramTypes.forall(x => allowedArgumentTypes.contains(x.typeSymbol)) &&
      allowedReturnTypes.contains(m.resultType.typeSymbol)

  val functionName = "JFunction".toTermName
  val functionPackage = "scala.compat.java8.".toTermName

  override def prepareForUnit(tree: tpd.Tree)(implicit ctx: Context): TreeTransform = {
    allowedReturnTypes   = defn.ScalaNumericValueClasses
    allowedArgumentTypes = defn.ScalaNumericValueClasses + defn.BooleanClass
    this
  }

  override def transformClosure(tree: Closure)(implicit ctx: Context, info: TransformerInfo): Tree = {
    tree.tpt match {
      case EmptyTree =>
        val m = tree.meth.tpe.widen.asInstanceOf[MethodType]

        if (shouldSpecialize(m)) {
          val interfaceName = (functionName ++ m.paramTypes.length.toString).specializedFor(m.resultType, m.paramTypes)
          // symbols loaded from classpath aren't defined in periods earlier than when they where loaded
          val interface = ctx.requiredClass(functionPackage ++ interfaceName)
          val tpt = tpd.TypeTree(interface.asType.typeRef)
          tpd.Closure(tree.env, tree.meth, tpt)
        } else tree
      case _ =>
        tree
    }
  }
}
