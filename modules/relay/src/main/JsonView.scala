package lila.relay

import play.api.libs.json.*

import lila.common.config.BaseUrl
import lila.common.Json.{ writeAs, given }
import lila.study.Chapter
import lila.user.Me
import lila.memo.PicfitUrl

final class JsonView(
    baseUrl: BaseUrl,
    markup: RelayMarkup,
    leaderboardApi: RelayLeaderboardApi,
    picfitUrl: PicfitUrl
)(using Executor):

  import JsonView.given
  import lila.study.JsonView.given

  given Writes[Option[RelayTour.Tier]] = Writes: t =>
    JsString(t.flatMap(RelayTour.Tier.keys.get) | "user")

  given OWrites[RelayTour] = OWrites: t =>
    Json
      .obj(
        "id"          -> t.id,
        "name"        -> t.name,
        "slug"        -> t.slug,
        "description" -> t.description,
        "createdAt"   -> t.createdAt
      )
      .add("tier" -> t.tier)
      .add("image" -> t.image.map(id => RelayTour.thumbnail(picfitUrl, id, _.Size.Large)))

  given OWrites[RelayTour.IdName] = Json.writes[RelayTour.IdName]

  given OWrites[RelayGroup.WithTours] = OWrites: g =>
    Json.obj(
      "name"  -> g.group.name,
      "tours" -> g.withShorterTourNames.tours
    )

  given OWrites[RelayRound] = OWrites: r =>
    Json
      .obj(
        "id"        -> r.id,
        "name"      -> r.name,
        "slug"      -> r.slug,
        "createdAt" -> r.createdAt
      )
      .add("finished" -> r.finished)
      .add("ongoing" -> (r.hasStarted && !r.finished))
      .add("startsAt" -> r.startsAt.orElse(r.startedAt))

  given Writes[RelayLeaderboard] = writeAs(_.players)

  def apply(
      trs: RelayTour.WithRounds,
      withUrls: Boolean = false,
      leaderboard: Option[RelayLeaderboard] = none
  ): JsObject =
    Json
      .obj(
        "tour" -> Json
          .toJsObject(trs.tour)
          .add("markup" -> trs.tour.markup.map(markup(trs.tour)))
          .add("url" -> withUrls.option(s"$baseUrl${trs.tour.path}"))
          .add("teamTable" -> trs.tour.teamTable),
        "rounds" -> trs.rounds.map: round =>
          if withUrls then withUrl(round.withTour(trs.tour), withTour = false) else apply(round)
      )
      .add("leaderboard" -> leaderboard)

  def apply(round: RelayRound): JsObject = Json.toJsObject(round)

  def withUrl(rt: RelayRound.WithTour, withTour: Boolean): JsObject =
    apply(rt.round) ++ Json
      .obj("url" -> s"$baseUrl${rt.path}")
      .add("tour" -> withTour.option(rt.tour))

  def withUrlAndGames(rt: RelayRound.WithTourAndStudy, games: List[Chapter.Metadata])(using
      Option[Me]
  ): JsObject =
    myRound(rt) ++
      Json.obj("games" -> games.map { g =>
        Json.toJsObject(g) + ("url" -> JsString(s"$baseUrl${rt.path}/${g._id}"))
      })

  def sync(round: RelayRound) = Json.toJsObject(round.sync)

  def myRound(r: RelayRound.WithTourAndStudy)(using me: Option[Me]) = Json
    .obj(
      "round" -> apply(r.relay)
        .add("url" -> s"$baseUrl${r.path}".some)
        .add("delay" -> r.relay.sync.delay),
      "tour"  -> r.tour,
      "study" -> Json.obj("writeable" -> me.exists(r.study.canContribute))
    )

  def makeData(
      trs: RelayTour.WithRounds,
      currentRoundId: RelayRoundId,
      studyData: lila.study.JsonView.JsData,
      group: Option[RelayGroup.WithTours],
      canContribute: Boolean,
      isSubscribed: Option[Boolean] = none[Boolean]
  ) = leaderboardApi(trs.tour).map: leaderboard =>
    JsonView.JsData(
      relay = apply(trs)
        .add("sync" -> (canContribute.so(trs.rounds.find(_.id == currentRoundId).map(_.sync))))
        .add("leaderboard" -> leaderboard)
        .add("group" -> group)
        .add("isSubscribed" -> isSubscribed),
      study = studyData.study,
      analysis = studyData.analysis
    )

object JsonView:

  case class JsData(relay: JsObject, study: JsObject, analysis: JsObject)

  given OWrites[SyncLog.Event] = Json.writes

  private given OWrites[RelayRound.Sync] = OWrites: s =>
    Json
      .obj(
        "ongoing" -> s.ongoing,
        "log"     -> s.log.events
      )
      .add("delay" -> s.delay) ++
      s.upstream.so {
        case url: RelayRound.Sync.UpstreamUrl => Json.obj("url" -> url.withRound.url)
        case RelayRound.Sync.UpstreamIds(ids) => Json.obj("ids" -> ids)
      }
