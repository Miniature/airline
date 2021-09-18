package controllers

import controllers.AuthenticationObject.Authenticated
import play.api.data.Forms.mapping
import play.api.data.Forms.number
import play.api.mvc._
import play.api.libs.json.Writes
import com.patson.model.{Airline, User, UserStatus}
import play.api.libs.json._
import com.patson.data.{AllianceSource, IpSource, UserSource}
import com.patson.util.AllianceCache

import javax.inject.Inject

class UserApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  implicit object UserWrites extends Writes[User] {
    def writes(user: User): JsValue = {
      var result = JsObject(List(
        "id" -> JsNumber(user.id),
        "userName" -> JsString(user.userName),
        "email" -> JsString(user.email),
        "status" -> JsString(user.status.toString()),
        "level" -> JsNumber(user.level),
        "creationTime" -> JsString(user.creationTime.getTime.toString()),
        "lastActiveTime" -> JsString(user.lastActiveTime.getTime.toString()),
        "airlineIds" -> JsArray(user.getAccessibleAirlines().map { airline => JsNumber(airline.id) })))

      user.adminStatus.foreach { adminStatus =>
        result = result + ("adminStatus" -> JsString(adminStatus.toString))
      }
      
      if (user.getAccessibleAirlines().isDefinedAt(0)) {
        AllianceSource.loadAllianceMemberByAirline(user.getAccessibleAirlines()(0)).foreach { allianceMember => //if this airline belongs to an alliance
          val allianceId = allianceMember.allianceId
          AllianceCache.getAlliance(allianceId).foreach { alliance =>
            result = result + ("allianceId" -> JsNumber(allianceId)) + ("allianceName" -> JsString(alliance.name)) + ("allianceRole" -> JsString(allianceMember.role.toString))
          }
        }
      }
        
      result
    }
  }
  // then in a controller
  def login = Authenticated { implicit request =>
    UserSource.updateUserLastActive(request.user)
    if (request.user.status == UserStatus.INACTIVE) {
      UserSource.updateUser(request.user.copy(status = UserStatus.ACTIVE))
    }

    var isSuperAdmin = false
    //check if it's super admin switching
    request.session.get("adminToken").foreach{ adminToken =>
      SessionUtil.getUserId(adminToken).foreach { adminId =>
        UserSource.loadUserById(adminId).foreach { user =>
          isSuperAdmin = user.isSuperAdmin
        }
      }
    }

    if (!isSuperAdmin) { //do not track if admin is switching, otherwise that would be confusing
      IpSource.saveUserIp(request.user.id, request.remoteAddress)
    }

    if (request.user.status == UserStatus.BANNED) {
      println(s"Banned user ${request.user} tried to login")
      Forbidden("User is banned")
    } else {
      Ok(Json.toJson(request.user)).withHeaders("Access-Control-Allow-Credentials" -> "true").withSession("userToken" -> SessionUtil.addUserId(request.user.id))
    }
  }
  
  def logout = Authenticated { implicit request =>
    Ok("logged out for " + request.user.id).withNewSession 
  }
  
 
}
