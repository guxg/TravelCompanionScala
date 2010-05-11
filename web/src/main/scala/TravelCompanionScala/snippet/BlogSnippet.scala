package TravelCompanionScala.snippet

import _root_.scala.xml.{NodeSeq, Text}

import _root_.net.liftweb._
import common.{Full, Empty}
import http._
import S._
import util._
import Helpers._

import TravelCompanionScala.model._
import java.text.SimpleDateFormat
import scala.collection.JavaConversions._

/**
 * Created by IntelliJ IDEA.
 * User: Ralf Muri
 * Date: 19.04.2010
 * Time: 09:02:05
 * To change this template use File | Settings | File Templates.
 */

// Set up a requestVar to track the STAGE object for edits and adds
object blogEntryVar extends RequestVar[BlogEntry](new BlogEntry())
object commentVar extends RequestVar[Comment](new Comment())

class BlogSnippet {
  def blogEntry = blogEntryVar.is

  def removeBlogEntry(entry: BlogEntry) {
    val e = Model.merge(entry)
    Model.remove(e)
    S.redirectTo("/blog/list")
  }

  def is_valid_Entry_?(toCheck: BlogEntry): Boolean = {
    val validationResult = validator.get.validate(toCheck)
    validationResult.foreach((e) => S.error(e.getPropertyPath + " " + e.getMessage))
    validationResult.isEmpty
  }

  def editBlogEntry(html: NodeSeq): NodeSeq = {
    def doEdit() = {
      if (is_valid_Entry_?(blogEntry)) {
        Model.mergeAndFlush(blogEntry)
        S.redirectTo("/blog/list")
      }
    }

    val currentEntry = blogEntry

    currentEntry.owner = UserManagement.currentUser
    currentEntry.lastUpdated = TimeHelpers.now

    val tours = Model.createNamedQuery[Tour]("findTourByOwner").setParams("owner" -> UserManagement.currentUser).findAll.toList
    val choices = List("" -> "- Keine -") ::: tours.map(tour => (tour.id.toString -> tour.name)).toList

    bind("entry", html,
      "title" -> SHtml.text(currentEntry.title, currentEntry.title = _),
      "content" -> SHtml.textarea(currentEntry.content, currentEntry.content = _),
      "tour" -> SHtml.select(choices, if (currentEntry.tour == null) Empty else Full(currentEntry.tour.id.toString), (tourId: String) => {if (tourId != "") currentEntry.tour = Model.getReference(classOf[Tour], tourId.toLong) else currentEntry.tour = null}),
      "owner" -> SHtml.text(currentEntry.owner.name, currentEntry.owner.name = _),
      "submit" -> SHtml.submit(?("save"), () => {blogEntryVar(currentEntry); doEdit}))
  }

  def listEntries(html: NodeSeq, entries: List[BlogEntry]): NodeSeq = {
    entries.flatMap(entry => bind("entry", html,
      "title" -> entry.title,
      "tour" -> {
        if (entry.tour == null) {
          NodeSeq.Empty
        } else {
          Text(?("blog.belongsTo") + " ") ++ SHtml.link("/tour/view", () => tourVar(entry.tour), Text(entry.tour.name))
        }
      },
      "content" -> entry.content,
      "edit" -> SHtml.link("/blog/edit", () => blogEntryVar(entry), Text(?("edit"))),
      "comments" -> SHtml.link("/blog/view", () => blogEntryVar(entry), Text(?("blog.comments"))),
      "remove" -> SHtml.link("/blog/remove", () => removeBlogEntry(entry), Text(?("remove"))),
      "preview" -> entry.content.substring(0, Math.min(entry.content.length, 50)),
      "readOn" -> SHtml.link("/blog/view", () => blogEntryVar(entry), Text(?("blog.readOn"))),
      "lastUpdated" -> new SimpleDateFormat("dd.MM.yyyy HH:mm").format(entry.lastUpdated),
      "creator" -> entry.owner.name))
  }

  def showEntry(html: NodeSeq): NodeSeq = {
    val currentEntry = blogEntry
    listEntries(html, List(blogEntry))
  }

  def showBlogEntriesFromTour(html: NodeSeq): NodeSeq = {
    val currentTour = tourVar.is
    val entries = Model.createNamedQuery[BlogEntry]("findEntriesByTour").setParams("tour" -> currentTour).findAll.toList
    listEntries(html, entries)
  }

  def listOtherEntries(html: NodeSeq): NodeSeq = {
    val entries = Model.createNamedQuery[BlogEntry]("findEntriesByOthers").setParams("owner" -> UserManagement.currentUser).findAll.toList
    listEntries(html, entries)
  }

  def listOwnEntries(html: NodeSeq): NodeSeq = {
    val entries = Model.createNamedQuery[BlogEntry]("findEntriesByOwner").setParams("owner" -> UserManagement.currentUser).findAll.toList
    listEntries(html, entries)
  }

  def is_valid_Comment_?(toCheck: Comment): Boolean = {
    val validationResult = validator.get.validate(toCheck)
    validationResult.foreach((e) => S.error(e.getPropertyPath + " " + e.getMessage))
    validationResult.isEmpty
  }

  def addComment(html: NodeSeq): NodeSeq = {
    def doAdd(c: Comment) = {
      if (is_valid_Comment_?(c))
        Model.mergeAndFlush(c)
    }

    val currentEntry = blogEntry
    val newComment = new Comment
    newComment.blogEntry = blogEntry
    newComment.member = UserManagement.currentUser
    newComment.dateCreated = TimeHelpers.now

    bind("comment", html,
      "content" -> SHtml.textarea(newComment.content, newComment.content = _),
      "submit" -> SHtml.submit(?("save"), () => {blogEntryVar(currentEntry); doAdd(newComment)}))
  }

  def doRemoveComment(comment: Comment) {
    val c = Model.merge(comment)
    Model.remove(c)
    S.redirectTo("/blog/view", () => blogEntryVar(c.blogEntry))
  }

  def showComments(html: NodeSeq): NodeSeq = {
    val comments = Model.createNamedQuery[Comment]("findCommentsByEntry").setParams("entry" -> blogEntry).findAll.toList
    comments.flatMap(comment =>
      bind("comment", html,
        "member" -> comment.member.name,
        "dateCreated" -> new SimpleDateFormat("dd.MM.yyyy HH:mm").format(comment.dateCreated),
        "content" -> comment.content,
        "options" -> {
          if ((comment.member == UserManagement.currentUser) || (blogEntry.owner == UserManagement.currentUser))
            bind("link", chooseTemplate("option", "list", html), "remove" -> SHtml.link("remove", () => {blogEntryVar(comment.blogEntry); doRemoveComment(comment)}, Text(?("remove"))))
          else
            NodeSeq.Empty
        }))
  }
}