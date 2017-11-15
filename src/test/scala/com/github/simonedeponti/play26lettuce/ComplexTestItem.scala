package com.github.simonedeponti.play26lettuce


case class ComplexTestItem(id: String,
                           name: Option[String],
                           data: Map[String, Seq[String]],
                           extraThing: SubItem,
                           extraOpt: Option[SubItem],
                           active: Boolean)
