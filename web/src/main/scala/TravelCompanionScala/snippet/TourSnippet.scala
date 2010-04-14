package TravelCompanionScala {
package snippet {

import _root_.scala.xml.{NodeSeq, Text}

import _root_.net.liftweb._
import http._
import S._
import common._
import util._
import Helpers._

import _root_.javax.persistence.{EntityExistsException, PersistenceException}
import TravelCompanionScala.model._
import Model._


/**
 * Created by IntelliJ IDEA.
 * User: Ralf Muri
 * Date: 09.04.2010
 * Time: 17:14:14
 * To change this template use File | Settings | File Templates.
 */


object TourEnum extends Enumeration {
  val OWN_TOURS = Value("OwnTours")
  val OTHERS_TOURS = Value("OthersTours")
}

class TourSnippet {

  // Set up a requestVar to track the TOUR object for edits and adds
  object tourVar extends RequestVar(new Tour())
  def tour = tourVar.is

  def deleteTour(html: NodeSeq): NodeSeq = {

    def doRemove() = {
      val t = Model.merge(tour)
      Model.remove(t)
      S.redirectTo("/tour/list")
    }

    val currentTour = tour

    bind("tour", html,
      "name" -> tour.name,
      "description" -> tour.description,
      "submit" -> SHtml.submit("Delete", () => {tourVar(currentTour); doRemove}))
  }

  def viewTour(html: NodeSeq): NodeSeq = {
    bind("tour", html, "name" -> tour.name, "description" -> tour.description)
  }

  def editTour(html: NodeSeq): NodeSeq = {
    def doEdit() = {
      Model.mergeAndFlush(tour)
      S.redirectTo("/tour/list")
    }

    val currentTour = tour

    if (currentTour.owner == null) {
      currentTour.owner = UserManagement.currentUser.open_!
    }

    bind("tour", html,
      "name" -> SHtml.text(currentTour.name, currentTour.name = _),
      "description" -> SHtml.textarea(currentTour.description, currentTour.description = _),
      "owner" -> SHtml.text(currentTour.owner.name, currentTour.owner.name = _),
      "submit" -> SHtml.submit("Speichern", () => {tourVar(currentTour); doEdit}))
  }

  def createTour(html: NodeSeq): NodeSeq = {
    editTour(html)
  }

  def listTours(html: NodeSeq): NodeSeq = {
    val which = S.attr("which").map(_.toString) openOr "AllTours"
    tours(TourEnum.withName(which)).flatMap(tour => bind("tour", html,
      "name" -> tour.name,
      "description" -> tour.description,
      "creator" -> tour.owner.name,
      "edit" -> SHtml.link("edit", () => {println(tour); tourVar(tour)}, Text(?("Edit"))),
      "view" -> SHtml.link("view", () => tourVar(tour), Text(?("View"))),
      "remove" -> SHtml.link("remove", () => tourVar(tour), Text(?("Remove")))))
  }

  private def tours(which: TourEnum.Value): List[Tour] = {
    val mid = UserManagement.currentUserId
    which match {
      case TourEnum.OWN_TOURS => Model.createQuery[Tour]("from Tour t where t.owner.id = :id").setParams("id" -> mid).findAll.toList
      // case TourEnum.OWN_TOURS => scala.collection.JavaConversions.asBuffer(UserManagement.currentUser.tours).toList
      case TourEnum.OTHERS_TOURS => return Model.createQuery[Tour]("from Tour t where t.owner.id != :id").setParams("id" -> mid).findAll.toList
    }
  }

}
}
}