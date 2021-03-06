package TravelCompanionScala.model

import net.liftweb.http._

import js.JE.{JsRaw}
import js.JsCmds
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._

import scala.xml._
import scala.xml.transform._

import net.liftweb.common._
import net.liftweb.util.Helpers._
import scala.collection.JavaConversions._
import javax.persistence.{PersistenceException, EntityExistsException}

/**
 * The UserManagement object provides login and registering mechanism.
 *
 * Further information on user management can be found on:
 * - Technologiestudium (github link) Chapter 4.2 [German]
 *
 * Specials:
 * Certain parts of the user management class are copied from ProtoUser class
 * http://github.com/dpp/liftweb/blob/master/framework/lift-persistence/lift-mapper/src/main/scala/net/liftweb/mapper/ProtoUser.scala
 *
 * @author Daniel Hobi
 *
 */

object UserManagement {
  lazy val testLogginIn = If(loggedIn_? _, S.??("must.be.logged.in"))

  /**
   *  this object holds the logged-in user or is empty. Access is only permitted within this class.
   */
  private object curUsr extends SessionVar[Box[Member]](Empty)

  /**
   * This object holds a temporary user which is used for binding form fields (Signup, login...) to the user.
   * its not an entity from the datebase and should never come in touch with the EntityManager.
   */
  private object tempUserVar extends RequestVar[Member](new Member)

  /**
   * This method gives back a new or already defined Member object.
   */
  def currentUser: Member = {
    if (curUsr.is.isDefined)
      curUsr.is.open_!
    else
      new Member
  }

  /**
   *  every URL starting with "user" is handled by this object
   */
  val basePath: List[String] = "user" :: Nil

  def loginSuffix = "login"

  lazy val loginPath = thePath(loginSuffix)

  def logoutSuffix = "logout"

  lazy val logoutPath = thePath(logoutSuffix)

  def signUpSuffix = "sign_up"

  lazy val registerPath = thePath(signUpSuffix)

  def profileSuffix = "profile"

  lazy val profilePath = thePath(profileSuffix)

  val defaultLocGroup = LocGroup("user")


  /**
   * Returns the URL of the "login" page
   */
  def loginPageURL = loginPath.mkString("/", "/", "")


  /**
   * Creating menues
   */
  def loginMenuLoc: Box[Menu] =
    Full(Menu(Loc("Login", loginPath, S.??("login"), loginMenuLocParams)))

  def logoutMenuLoc: Box[Menu] =
    Full(Menu(Loc("Logout", logoutPath, S.??("logout"), logoutMenuLocParams)))

  def createUserMenuLoc: Box[Menu] =
    Full(Menu(Loc("CreateUser", registerPath, S.??("sign.up"), createUserMenuLocParams)))

  def profileMenuLoc: Box[Menu] =
    Full(Menu(Loc("Profile", profilePath, S.?("member.profile"), profileMenuLocParams)))

  def thePath(end: String): List[String] = basePath ::: List(end)

  /**
   * The LocParams for the menu item for login.
   * Overwrite in order to add custom LocParams. Attention: Not calling super will change the default behavior!
   */
  protected def loginMenuLocParams: List[LocParam[Unit]] =

    If(notLoggedIn_? _, S.??("already.logged.in")) ::
            Template(() => wrapIt(login)) :: defaultLocGroup ::
            Nil

  /**
   * The LocParams for the menu item for register.
   * Overwrite in order to add custom LocParams. Attention: Not calling super will change the default behavior!
   */
  protected def createUserMenuLocParams: List[LocParam[Unit]] =
    Template(() => wrapIt(signup)) ::
            If(notLoggedIn_? _, S.??("logout.first")) :: defaultLocGroup ::
            Nil

  /**
   * The LocParams for the menu item for logout.
   * Overwrite in order to add custom LocParams. Attention: Not calling super will change the default behavior!
   */
  protected def logoutMenuLocParams: List[LocParam[Unit]] =
    Template(() => wrapIt(logout)) ::
            testLogginIn :: defaultLocGroup ::
            Nil

  /**
   * The LocParams for the menu item for profile.
   * Overwrite in order to add custom LocParams. Attention: Not calling super will change the default behavior!
   */
  protected def profileMenuLocParams: List[LocParam[Unit]] =
    Template(() => wrapIt(editProfile)) ::
            testLogginIn :: defaultLocGroup ::
            Nil


  /**
   * Defines menu sitemap
   */
  def menus: List[Menu] = sitemap

  lazy val sitemap: List[Menu] = List(loginMenuLoc, logoutMenuLoc, createUserMenuLoc, profileMenuLoc).flatten(a => a)

  /**
   * Checks if user is logged in or not
   */
  def notLoggedIn_? = !loggedIn_?

  def loggedIn_? = !curUsr.get.isEmpty

  /**
   * Defines a wrapper for binding purposes
   */
  def screenWrap: Box[Node] = Full(<lift:surround with="default" at="content">
      <lift:bind/>
  </lift:surround>)

  protected def wrapIt(in: NodeSeq): NodeSeq =
    screenWrap.map(new RuleTransformer(new RewriteRule {
      override def transform(n: Node) = n match {
        case e: Elem if "bind" == e.label && "lift" == e.prefix => in
        case _ => n
      }
    })) openOr in


  /**
   * Login form
   */
  def loginXhtml = {
    (<p>
      {S.?("member.login")}
    </p>
            <lift:Msgs>
                <lift:error_msg/>
            </lift:Msgs>
            <form method="post" action={S.uri}>
              <table class="form">
                <tbody>
                  <tr>
                    <td class="desc">
                      <label for="name">
                        {S.?("member.username")}
                      </label>
                    </td>
                    <td>
                        <user:username/>
                    </td>
                  </tr>
                  <tr>
                    <td class="desc">
                      <label for="password">
                        {S.??("password")}
                      </label>
                    </td>
                    <td>
                        <user:password/>
                    </td>
                  </tr>
                  <tr>
                    <td></td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
              <div class="bottomnavi">
                  <user:submit/>
                <a href="/index">
                  {S.?("cancel")}
                </a>
              </div>
            </form>)
  }

  /**
   * Logs in user
   * @param in a member
   */
  def logInUser(user: Member) = {
    curUsr.set(Full(user))
    tempUserVar(user)
    S.redirectTo("/")
  }

  /**
   * Logs out the current user
   */
  def logout = {
    curUsr.set(Empty)
    tempUserVar(new Member)
    S.redirectTo("/")
  }

  /**
   * Authentification
   */
  def login = {
    /**
     * Tries to authentificate the user and logs him in if he was found in the database
     */
    def checkLogin() {
      val tryUser = Model.createQuery[Member]("SELECT m from Member m where m.name = :name and m.password = :password").setParams("name" -> tempUserVar.is.name, "password" -> tempUserVar.is.password).findOne
      if (tryUser.isDefined) {
        logInUser(tryUser.get)
      } else {
        S.error({
          S.?("member.invalid.credentials")
        })
      }
    }

    val current = tempUserVar.is

    /**
     * Renders the login form
     */
    bind("user", loginXhtml,
      "username" -> SHtml.text(current.name, current.name = _),
      "password" -> SHtml.password(current.password, current.password = _),
      "submit" -> SHtml.submit(S.??("log.in"), () => {
        tempUserVar(current);
        checkLogin
      }))
  }

  /**
   * Registration form
   */
  def memberXhtml() = {
    (<form method="post" action={S.uri}>
      <h2>
          <user:title/>
      </h2>
      <p>
        {S.?("member.register")}
      </p>
      <lift:Msgs>
          <lift:error_msg/>
      </lift:Msgs>
      <table class="form">
        <tbody>
          <tr>
            <td class="desc">
              <label for="username">
                {S.?("member.username")}
              </label>
            </td>
            <td>
                <user:username id="username"/> <span id="checkUsername"></span>
            </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="firstname">
                {S.??("first.name")}
              </label>
            </td> <td>
              <user:firstname/>
          </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="lastname">
                {S.??("last.name")}
              </label>
            </td> <td>
              <user:lastname/>
          </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="street">
                {S.?("street")}
              </label>
            </td> <td>
              <user:street/>
          </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="zipcode">
                {S.?("zipcode")}
              </label>
            </td> <td>
              <user:zipcode/>
          </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="city">
                {S.?("city")}
              </label>
            </td> <td>
              <user:city/>
          </td>
          </tr>

          <tr>
            <td class="desc">
              <label for="email">
                {S.??("email.address")}
              </label>
            </td> <td>
              <user:email/>
          </td>
          </tr>
          <tr>
            <td class="desc">
              <label for="password">
                {S.??("password")}
              </label>
            </td> <td>
              <user:password/>
          </td>
          </tr>
        </tbody>
      </table>
      <div class="bottomnavi">
          <user:submit/>
        <a href="/index">
          {S.?("cancel")}
        </a>
      </div>
    </form>)
  }

  /**
   * Registration
   */
  def signup() =
    {
      /**
       * Validates user input and register if there were no errors
       */
      def testSignup() {
        val validationResult = Validator.get.validate(tempUserVar.is)
        if (validationResult.isEmpty) {
          try {
            logInUser(Model.mergeAndFlush(tempUserVar.is))
            S.notice(S.??("welcome"))
            S.redirectTo("/")
          } catch {
            case ee: EntityExistsException => S.error("That user already exists.")
            case pe: PersistenceException => S.error("Error adding user")
          }
        } else {
          validationResult.foreach((e) => S.error(e.getPropertyPath + " " + e.getMessage))
        }
      }

      val current = tempUserVar.is

      /**
       * Checks if a username already exists and returns a visual output
       * This method is called by SHtml.ajaxText()
       */
      def checkUsername(username: String) = {

        current.name = username

        val tryUser = Model.createQuery[Member]("SELECT m from Member m where m.name = :name").setParams("name" -> username).findOne
        var message: NodeSeq = <img src="../images/tick.png" alt="Username ok" title="Username ok"/>
        var inputclass: String = ""
        if (tryUser.isDefined) {
          message = <img src="../images/cross.png" alt="Username exists" title="Username exists"/>
          inputclass = "inputerror"
        }
        JsCmds.SetHtml("checkUsername", message) &
                JsRaw("$('#username').attr('class', '" + inputclass + "');").cmd &
                JsCmds.JsHideId("lift__noticesContainer__")
      }

      /**
       * Render register form
       */
      bind("user",
        memberXhtml,
        "title" -> S.??("sign.up"),
        "username" -%> SHtml.ajaxText(current.name, checkUsername),
        "firstname" -> SHtml.text(current.forename, current.forename = _),
        "lastname" -> SHtml.text(current.surname, current.surname = _),
        "street" -> SHtml.text(current.street, current.street = _),
        "zipcode" -> SHtml.text(current.zipcode, current.zipcode = _),
        "city" -> SHtml.text(current.city, current.city = _),
        "email" -> SHtml.text(current.email, current.email = _),
        "password" -> SHtml.password(current.password, current.password = _),
        "submit" -> SHtml.submit(S.??("sign.up"), () => {
          tempUserVar(current);
          testSignup
        }))
    }

  def editProfile =
    {
      /**
       * Validates user input and edit the user object if there were no errors
       */
      def testSave() {
        val validationResult = Validator.get.validate(tempUserVar.is)
        if (validationResult.isEmpty) {
          try {
            tempUserVar(Model.mergeAndFlush(tempUserVar.is))
            S.notice(S.??("profile.updated"))
            curUsr.set(Full(tempUserVar.is))
            S.redirectTo("/")
          } catch {
            case ee: EntityExistsException => S.error(S.?("userexists"))
            case pe: PersistenceException => S.error(S.?("useradderr"))
          }
        } else {
          validationResult.foreach((e) => S.error(e.getPropertyPath + " " + e.getMessage))
        }
      }

      val current = currentUser
       /**
       * Render register form
       */
      bind("user",
        memberXhtml,
        "title" -> S.?("member.editProfile"),
        "username" -> SHtml.text(current.name, current.name = _),
        "firstname" -> SHtml.text(current.forename, current.forename = _),
        "lastname" -> SHtml.text(current.surname, current.surname = _),
        "street" -> SHtml.text(current.street, current.street = _),
        "zipcode" -> SHtml.text(current.zipcode, current.zipcode = _),
        "city" -> SHtml.text(current.city, current.city = _),
        "email" -> SHtml.text(current.email, current.email = _),
        "password" -> SHtml.password(current.password, current.password = _),
        "submit" -> SHtml.submit(S.?("save"), () => {
          tempUserVar(current);
          testSave
        }))
    }

}