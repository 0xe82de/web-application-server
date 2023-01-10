package controller;

import db.DataBase;
import http.HttpRequest;
import http.HttpResponse;
import http.HttpSession;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class ListUserController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(ListUserController.class);

    @Override
    protected void doGet(HttpRequest request, HttpResponse response) {
        if (!isLogined(request.getSession())) {
            response.sendRedirect("/user/login.html");
            return;
        }

        Collection<User> users = DataBase.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1'>");
        users.forEach(user -> {
            sb.append("<tr>");
            sb.append("<td>" + user.getUserId() + "</td>");
            sb.append("<td>" + user.getName() + "</td>");
            sb.append("<td>" + user.getEmail() + "</td>");
            sb.append("</tr>");
        });
        sb.append("</table>");

        response.forwardBody(sb.toString());
    }

    private boolean isLogined(HttpSession session) {
        Object user = session.getAttribute("user");
        return user != null;
    }
}
