/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.http
import javax.inject._

import play.api.inject.Binding
import play.api.mvc.EssentialFilter
import play.api.{ Configuration, Environment }
import play.utils.Reflect

trait SystemFilter extends EssentialFilter

/**
 * System filters are an internal part of Play API, and should not be
 * extended or used by modules or applications.
 */
trait SystemFilters {
  def filters: Seq[SystemFilter]
}

private[play] trait JavaSystemFilters extends SystemFilters

object SystemFilters {

  def bindingsFromConfiguration(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Reflect.bindingsFromConfiguration[SystemFilters, JavaSystemFilters, JavaSystemFiltersAdapter, JavaSystemFiltersDelegate, NoSystemFilters](environment, configuration, "play.http.systemfilters", "SystemFilters")
  }

  def apply(list: SystemFilter*): SystemFilters = new SystemFilters {
    override val filters: Seq[SystemFilter] = list
  }
}

abstract class AbstractSystemFilters(val filters: SystemFilter*) extends SystemFilters

private class NoSystemFilters @Inject() () extends AbstractSystemFilters

private class JavaSystemFiltersAdapter @Inject() (underlying: SystemFilter*) extends AbstractSystemFilters(underlying: _*)

private class JavaSystemFiltersDelegate @Inject() (delegate: SystemFilters) extends JavaSystemFilters {
  override def filters: Seq[SystemFilter] = delegate.filters
}

private[play] class DefaultSystemFilters(val filters: SystemFilter*) extends HttpFilters

