package ru.otus.sc.json

import play.api.libs.json._

import scala.reflect.ClassTag

trait AdtProtocol {
  def objectFormat[T <: Singleton](t: T): OFormat[T] =
    OFormat(
      {
        case JsObject(_) => JsSuccess(t)
        case v           => JsError(s"Object expected, but $v found.")
      },
      _ => JsObject.empty
    )

  case class AdtCase[T](name: String, classTag: ClassTag[T], format: OFormat[T]) {
    def toJson(field: String, a: Any): Option[JsObject] = {
      implicit val implClassTag: ClassTag[T] = classTag

      a match {
        case t: T => Some(format.writes(t) + (field -> JsString(name)))
        case _    => None
      }
    }
  }

  def adtCase[T](name: String)(implicit classTag: ClassTag[T], format: OFormat[T]): AdtCase[T] =
    AdtCase(name, classTag, format)

  def adtFormat[T](typeField: String)(cases: AdtCase[_ <: T]*): OFormat[T] = {
    val reads: Reads[T] = Reads {
      case o @ JsObject(map) =>
        map.get(typeField) match {
          case Some(JsString(name)) =>
            cases.find(_.name == name) match {
              case Some(adtCase) => adtCase.format.reads(o)
              case None          => JsError(s"Can't find parser for type $name")
            }
          case Some(value) => JsError(__ \ "typeField", s"Unexpected type field value $value")
          case None        => JsError(__ \ "typeField", "Can't find type field")
        }
      case _ => JsError("Object expected.")
    }

    val writes: OWrites[T] = OWrites { t =>
      cases
        .flatMap(_.toJson(typeField, t))
        .headOption
        .getOrElse(throw new RuntimeException(s"Can't handle value $t"))
    }

    OFormat(reads, writes)
  }
}
