/**
 * This file is part of objc2swift.
 * https://github.com/yahoojapan/objc2swift
 *
 * Copyright (c) 2015 Yahoo Japan Corporation
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package org.objc2swift.converter

import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTreeProperty
import org.objc2swift.converter.ObjCParser._

import scala.collection.JavaConversions._

/**
 * Implements visit methods for enum contexts.
 */
trait EnumVisitor {
  this: ObjC2SwiftBaseConverter
    with TypeVisitor
    with UtilMethods =>

  private val identifiers = new ParseTreeProperty[String]()

  def findDeclarationSpecifiers(ctx: RuleContext): Option[DeclarationSpecifiersContext] =
    ctx match {
      case c: DeclarationSpecifiersContext => Some(c)
      case c: TranslationUnitContext => None
      case _ => findDeclarationSpecifiers(ctx.parent)
    }

  def getClassName(ctx: DeclarationSpecifiersContext): String =
    Option(ctx.typeSpecifier()).filter(_.size >= 2).flatMap { list =>
      Option(list.last.className())
    }.map(visit).getOrElse("")

  /**
   * Get name of enumerator.
   * @param ctx parse tree
   * @return
   */
  def getEnumName(ctx: EnumSpecifierContext): String =
    {
      Option(ctx.identifier()).map(visit) orElse
      findDeclarationSpecifiers(ctx).map(getClassName)
    }.getOrElse("")

  override def visitEnumSpecifier(ctx: EnumSpecifierContext): String =
    Some(getEnumName(ctx))
      .filter(_.nonEmpty)
      .map(visitEnumSpecifier(ctx, _))
      .getOrElse("")

  /**
   * Return translated text of enum_specifier context.
   *
   * @param ctx parse tree
   * @param identifier enum id
   * @return translated text
   */
  def visitEnumSpecifier(ctx: EnumSpecifierContext, identifier: String): String = {
    val builder = List.newBuilder[String]
    val typeStr = for {
      c1 <- Option(ctx.typeName())
      c2 <- Option(c1.specifierQualifierList())
      c3 <- Option(c2.typeSpecifier()).map(_.toList)
    } yield processTypeSpecifierList(c3)

    // save this enum id
    identifiers.put(ctx, identifier)

    builder += s"enum $identifier : ${typeStr.getOrElse("Int")}"
    builder += Option(ctx.enumeratorList()).map(visit).getOrElse("")

    builder.result().mkString
  }

  /**
   * Returns translated text of enumerator_list context.
   *
   * @param ctx the parse tree
   **/
  override def visitEnumeratorList(ctx: EnumeratorListContext): String =
    s" {\n${ctx.enumerator().map(visit).mkString("\n")}\n}"

  /**
   * Returns translated text of enumerator context.
   *
   * @param ctx the parse tree
   **/
  override def visitEnumerator(ctx: EnumeratorContext): String =
    s"${indent(ctx)}case ${getEnumIdentifier(ctx)}${getEnumConstant(ctx)}"

  /**
   * Returns translated text of identifier under the enumerator context
   *
   * @param ctx the parse tree
   * @return translated text
   */
  private def getEnumIdentifier(ctx: EnumeratorContext): String = {
    val origId = visit(ctx.identifier())
    val enumId = identifiers.get(ctx.parent.parent)
    val digitId = "[0-9].*".r

    // Trim duplicate prefix
    origId.stripPrefix(enumId) match {
      case digitId() => origId
      case s         => s
    }
  }

  /**
   * Returns translated text of constant_expression under the enumerator context
   *
   * @param ctx the parse tree
   * @return translated text
   */
  private def getEnumConstant(ctx: EnumeratorContext): String =
    Option(ctx.constantExpression()).map(c => s" = ${visit(c)}").getOrElse("")
}
