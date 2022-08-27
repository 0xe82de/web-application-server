package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());


        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            Map<String, String> headers = parsingHeaders(br);

//            if (headers != null) {
//                log.debug("HTTP Header");
//                for (Map.Entry<String, String> headerInfo : headers.entrySet()) {
//                    log.debug("{}: {}", headerInfo.getKey(), headerInfo.getValue());
//                }
//            }

            String method = headers.get("method");
            String uri = headers.get("uri");

            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = null;

            if (uri.contains("/user/create")) {
                String params = null;
                if ("GET".equals(method)) {
                    int index = uri.indexOf("?");
                    params = uri.substring(index + 1);
                } else if ("POST".equals(method)) {
                    int contentLength = getContentLength(headers);
                    params = IOUtils.readData(br, contentLength);
                }
                Map<String, String> registerInfos = HttpRequestUtils.parseQueryString(params);
                User user = new User(registerInfos.get("userId"), registerInfos.get("password"), registerInfos.get("name"), registerInfos.get("email"));
                DataBase.addUser(user);
                log.debug("[{}] 회원가입 성공={}", method, user);

                String page = "/index.html";
                body = getBody(page);
                response302Header(dos, page);
                responseBody(dos, body);
            } else if ("/user/login".equals(uri) && "POST".equals(method)) {
                int contentLength = getContentLength(headers);
                String loginParams = IOUtils.readData(br, contentLength);
                Map<String, String> loginInfos = HttpRequestUtils.parseQueryString(loginParams);
                String userId = loginInfos.get("userId");
                String password = loginInfos.get("password");

                User findUser = DataBase.findUserById(userId);
                boolean logined = findUser != null && findUser.getPassword().equals(password);
                String page = logined ? "/index.html" : "/user/login_failed.html";
                log.debug("로그인 {}", logined ? "성공" : "실패");

                responseLoginHeader(dos, page, logined);
                body = getBody(page);
                responseBody(dos, body);
            } else if ("/user/list".equals(uri)) {
                String cookieField = headers.get("Cookie");
                if (cookieField == null) {
                    response302Header(dos, "/user/login.html");
                }

                Map<String, String> cookies = HttpRequestUtils.parseCookies(cookieField);
                String loginedStr = cookies.get("logined");
                if (loginedStr == null || (!"true".equals(loginedStr) && !"false".equals(loginedStr))) {
                    response302Header(dos, "/user/login.html");
                } else {
                    if (Boolean.parseBoolean(loginedStr)) {
                        body = getBody("/user/list.html");
                        response200Header(dos, body.length);
                    } else {
                        response302Header(dos, "/user/login.html");
                    }
                }
                responseBody(dos, body);
            } else if ("/css/styles.css".equals(uri)) {
                body = getBody("/css/styles.css");
                responseCssHeader(dos, body.length);
                responseBody(dos, body);
            } else {
                body = getBody(uri);
                response200Header(dos, body.length);
                responseBody(dos, body);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseCssHeader(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
//            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseLoginHeader(DataOutputStream dos, String page, boolean logined) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: http://localhost:8080" + page + "\r\n");
            dos.writeBytes("Set-Cookie: logined=" + logined);
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private static int getContentLength(Map<String, String> headers) {
        return Integer.parseInt(headers.get("Content-Length"));
    }

    private void response302Header(DataOutputStream dos, String page) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: http://localhost:8080" + page + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private static byte[] getBody(String uri) throws IOException {
        if ("/".equals(uri)) {
            return "Hello World".getBytes();
        }

        return Files.readAllBytes(new File("./webapp" + uri).toPath());
    }

    private Map<String, String> parsingHeaders(BufferedReader br) throws IOException {
        Map<String, String> headers = new HashMap<>();

        String requestLine = br.readLine();
        if ("".equals(requestLine) || requestLine == null) {
            return null;
        }

        String[] requestLineTokens = requestLine.split(" ");
        headers.put("method", requestLineTokens[0]);
        headers.put("uri", requestLineTokens[1]);
        headers.put("httpVersion", requestLineTokens[2]);

        String headerField = "";
        while (!"".equals(headerField = br.readLine())) {
            String[] headerFiledTokens = headerField.split(": ");
            headers.put(headerFiledTokens[0], headerFiledTokens[1]);
        }

        return headers;
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
