package com.payalabs.scalajs.react.bridge

import scala.language.implicitConversions
import scala.language.experimental.macros
import scala.reflect.macros.blackbox._
import scala.scalajs.js
import js.Dynamic.global
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement

/**
 * See project's [README.md](https://github.com/payalabs/scalajs-react-bridge)
 */

abstract class ReactBridgeComponent {

  /**
    * JS namespace for the underlying component.
    *
    * Certain libraries such as `ReactBootstrap` define components in a namespace.
    * This property allows specifying a namespace for the bridge to look for
    * the underlying component's function.
    *
    * See [ReactBootstrapComponent](https://github.com/payalabs/scalajs-react-bridge-example/src/main/scala/com/payalabs/scalajs/react/bridge/elements/ReactBootstrapBridge.scala)
    * in the example app for typical usage.
    *
    * A '.' separated string of namespace (a package-like string)
    */
  protected lazy val componentNamespace: String = ""


  // Class names generated by Scala for inner types (such as one used from our test cases have the following form)
  // com.payalabs.scalajs.react.bridge.test.ReactBridgeComponentTest$TestComponent$20$
  // We would have liked to use getSimpleName, but it fails miserably (see https://github.com/scala/bug/issues/2034)
  // So, we use our own "parsing" to extract the simple name
  protected lazy val componentName: String = this.getClass.getName.split('.').last.split('$').reverse.dropWhile(_.forall(_.isDigit)).head

  protected lazy val jsComponent = {
    val componentPrefixes = if (componentNamespace.trim.isEmpty) Array[String]() else componentNamespace.split('.')

    val componentFunction = componentPrefixes.foldLeft(global) {
      _.selectDynamic(_)
    }.selectDynamic(componentName)

    JsComponent[js.Object, Children.Varargs, Null](componentFunction)
  }

  def auto: WithProps = macro ReactBridgeComponent.autoImpl

  def autoNoChildren: WithPropsNoChildren = macro ReactBridgeComponent.autoNoChildrenImpl

  def autoNoTagMods: WithPropsAndTagsMods = macro ReactBridgeComponent.autoNoTagModsImpl

  def autoNoTagModsNoChildren: VdomElement = macro ReactBridgeComponent.autoNoTagModsNoChildrenImpl

}

object ReactBridgeComponent {
  def autoImpl(c: Context): c.Expr[WithProps] = {
    import c.universe._

    val ctor = symbolOf[WithProps]

    c.Expr(q"new $ctor(${c.prefix.tree}.jsComponent, ${propsObject(c)})")
  }

  def autoNoChildrenImpl(c: Context): c.Expr[WithPropsNoChildren] = {
    import c.universe._

    val ctor = symbolOf[WithPropsNoChildren]
    c.Expr(q"new $ctor(${c.prefix.tree}.jsComponent, ${propsObject(c)})")
  }

  def autoNoTagModsImpl(c: Context): c.Expr[WithPropsAndTagsMods] = {
    import c.universe._

    val ctor = symbolOf[WithPropsAndTagsMods]
    c.Expr(q"new $ctor(${c.prefix.tree}.jsComponent, ${propsObject(c)}, _root_.scala.List())")
  }

  def autoNoTagModsNoChildrenImpl(c: Context): c.Expr[VdomElement] = {
    import c.universe._

    val ctor = symbolOf[WithPropsAndTagModsAndChildren]
    c.Expr(q"new $ctor(${c.prefix.tree}.jsComponent, ${propsObject(c)}, _root_.scala.List()).apply")
  }

  private def propsObject(c: Context): c.Expr[js.Object] = {
    import c.universe._
    c.Expr(q"_root_.com.payalabs.scalajs.react.bridge.ReactBridgeComponent.propsToDynamic(${computeParams(c)})")
  }

  /**
    * Convert params passed to the apply method into their JS equivalent and pack them into a js.Dynamic
    * @param c
    * @return
    * @see JsWriter
    */
  private def computeParams(c: Context): c.Expr[List[(String, js.Any)]] = {
    import c.universe._

    val props = {
      val params = c.internal.enclosingOwner.asMethod.paramLists.flatten.filter(!_.isImplicit)
      val convertedProps = params.map { param =>
        val paramType = c.typecheck(Ident(param.name)).tpe
        val converted = {
          val conv = c.inferImplicitValue(appliedType(typeOf[JsWriter[_]], paramType :: Nil))
          q"$conv.toJs(${param.name.toTermName})"
        }
        (param.name.toString, converted)
      }

      convertedProps
    }

    c.Expr[List[(String, js.Any)]](q"$props")
  }

  def propsToDynamic(props: List[(String, js.Any)]): js.Object =
    js.Dictionary(props: _*).asInstanceOf[js.Object]
}
