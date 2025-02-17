package views.html
package userTournament

import lila.app.templating.Environment.{ given, * }
import lila.app.ui.ScalatagsTemplate.{ *, given }
import lila.common.paginator.Paginator
import lila.user.User

object upcoming:

  def apply(u: User, pager: Paginator[lila.tournament.Tournament])(using PageContext) =
    bits.layout(
      u = u,
      title = s"${u.username} upcoming tournaments",
      path = "upcoming"
    ):
      if pager.nbResults == 0 then div(cls := "box-pad")(trans.nothingToSeeHere())
      else
        div(cls := "tournament-list")(
          table(cls := "slist")(
            thead(
              tr(
                th(cls := "count")(pager.nbResults),
                th(colspan := 2)(
                  h1(frag(userLink(u, withOnline = true)), " • ", trans.team.upcomingTournaments())
                ),
                th(trans.players())
              )
            ),
            tbody:
              pager.currentPageResults.map: t =>
                tr(
                  td(cls := "icon")(iconTag(tournamentIcon(t))),
                  views.html.tournament.finishedList.header(t),
                  td(momentFromNow(t.startsAt)),
                  td(cls := "text", dataIcon := licon.User)(t.nbPlayers.localize)
                )
          )
        )
