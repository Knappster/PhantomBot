/*
 * Copyright (C) 2016-2023 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package tv.phantombot.httpserver;

import com.gmt2001.PathValidator;
import com.gmt2001.Reflect;
import com.gmt2001.httpwsserver.HTTPWSServer;
import com.gmt2001.httpwsserver.HttpRequestHandler;
import com.gmt2001.httpwsserver.HttpServerPageHandler;
import com.gmt2001.httpwsserver.auth.HttpAuthenticationHandler;
import com.gmt2001.httpwsserver.auth.HttpBasicAuthenticationHandler;
import com.knappster.StreamLabsAPIv2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.json.JSONException;

import tv.phantombot.CaselessProperties;

/**
 *
 * @author gmt2001
 */
public class HTTPStreamLabsOAuthHandler implements HttpRequestHandler {

    private HttpAuthenticationHandler authHandler;

    public HTTPStreamLabsOAuthHandler() {
        this.authHandler = new HttpBasicAuthenticationHandler("PhantomBot Web Panel",
                CaselessProperties.instance().getProperty("paneluser", "panel"),
                CaselessProperties.instance().getProperty("panelpassword", "panel"), "/panel/login/");
    }

    @Override
    public HttpRequestHandler register() {
        HttpServerPageHandler.registerHttpHandler("/streamlabsoauth", this);
        return this;
    }

    public void updateAuth() {
        this.authHandler = new HttpBasicAuthenticationHandler("PhantomBot Web Panel",
                CaselessProperties.instance().getProperty("paneluser", "panel"),
                CaselessProperties.instance().getProperty("panelpassword", "panel"), "/panel/login/");
    }

    @Override
    public HttpAuthenticationHandler getAuthHandler() {
        return authHandler;
    }

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        QueryStringDecoder qsd = new QueryStringDecoder(req.uri());

        if (!req.method().equals(HttpMethod.GET)) {
            com.gmt2001.Console.debug.println("405 " + req.method().asciiName() + ": " + qsd.path());
            HttpServerPageHandler.sendHttpResponse(ctx, req,
                    HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.METHOD_NOT_ALLOWED));
            return;
        }

        if (req.uri().startsWith("/panel/checklogin")) {
            FullHttpResponse res = HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.NO_CONTENT);
            String origin = req.headers().get(HttpHeaderNames.ORIGIN);
            res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

            com.gmt2001.Console.debug.println("204 " + req.method().asciiName() + ": " + qsd.path());
            HttpServerPageHandler.sendHttpResponse(ctx, req, res);
            return;
        }

        try {
            Map<String, List<String>> params = qsd.parameters();

            if (!params.containsKey("code")) {
                streamlabsAuthorize(ctx, req, qsd);
                return;
            } else {
                getAuthToken(ctx, req, qsd);
                return;
            }
        } catch (Exception ex) {
            com.gmt2001.Console.debug.println("500 " + req.method().asciiName() + ": " + qsd.path());
            com.gmt2001.Console.debug.printStackTrace(ex);
            HttpServerPageHandler.sendHttpResponse(ctx, req,
                    HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void streamlabsAuthorize(
            ChannelHandlerContext ctx,
            FullHttpRequest req,
            QueryStringDecoder qsd) {
        FullHttpResponse res = HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.SEE_OTHER);

        String uri = StreamLabsAPIv2.instance().GetAuthorizeURI(req);

        res.headers().set(HttpHeaderNames.LOCATION, uri);

        com.gmt2001.Console.debug.println("303 " + req.method().asciiName() + ": " + qsd.path());
        HttpServerPageHandler.sendHttpResponse(ctx, req, res);
    }

    private void getAuthToken(
            ChannelHandlerContext ctx,
            FullHttpRequest req,
            QueryStringDecoder qsd) throws JSONException, IllegalStateException, URISyntaxException {
        if (!qsd.parameters().containsKey("code") || qsd.parameters().get("code").isEmpty()
                || qsd.parameters().get("code").get(0).isBlank()) {
            HttpServerPageHandler.sendHttpResponse(ctx, req, HttpServerPageHandler
                    .prepareHttpResponse(HttpResponseStatus.OK, "[]".getBytes(Charset.forName("UTF-8")), "json"));
            return;
        }

        try {
            String code = qsd.parameters().get("code").get(0);

            Boolean tokenSuccess = StreamLabsAPIv2.instance().RequestAccessToken(req, code);

            if (tokenSuccess == true) {
                FullHttpResponse res = HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.SEE_OTHER);

                String host = req.headers().get(HttpHeaderNames.HOST);

                if (host == null) {
                    host = "";
                } else if (HTTPWSServer.instance().isSsl()) {
                    host = "https://" + host;
                } else {
                    host = "http://" + host;
                }

                res.headers().set(HttpHeaderNames.LOCATION, host + "/panel");

                com.gmt2001.Console.debug.println("303 " + req.method().asciiName() + ": " + qsd.path());
                HttpServerPageHandler.sendHttpResponse(ctx, req, res);
            }

        } catch (Exception ex) {
            com.gmt2001.Console.debug.println("500");
            com.gmt2001.Console.debug.printStackTrace(ex);
            HttpServerPageHandler.sendHttpResponse(ctx, req,
                    HttpServerPageHandler.prepareHttpResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
