/* astyle --style=java --indent=spaces=4 */

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
package com.knappster;

import com.gmt2001.HttpRequest;
import com.gmt2001.httpclient.HttpClient;
import com.gmt2001.httpclient.HttpClientResponse;
import com.gmt2001.httpclient.URIUtil;
import com.gmt2001.httpwsserver.HTTPWSServer;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import tv.phantombot.CaselessProperties;
import tv.phantombot.CaselessProperties.Transaction;
import zipkin2.Endpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * Communicates with the Twitch Alerts v1 API server. (StreamLabs)  Currently only
 * supports the GET donations method.
 *
 * @author knappster
 */
public class StreamLabsAPIv2 {

    private static StreamLabsAPIv2 instance;
    private static final String APIURL = "https://streamlabs.com/api/v2.0";
    private String accessToken = "";
    private int donationPullLimit = 5;
    private String currencyCode = "";

    public static StreamLabsAPIv2 instance() {
        if (instance == null) {
            instance = new StreamLabsAPIv2();
        }

        return instance;
    }

    private StreamLabsAPIv2() {
        Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());
    }

    private JSONObject requestJsonResponse(
            HttpMethod method,
            String endpoint,
            Map<String, String> queryParams,
            Map<String, String> bodyData) throws JSONException, URISyntaxException {
        return this.requestJsonResponse(method, endpoint, queryParams, bodyData, false);
    }

    private JSONObject requestJsonResponse(
            HttpMethod method,
            String endpoint,
            Map<String, String> queryParams,
            Map<String, String> bodyData,
            Boolean unauthRequest) throws JSONException, URISyntaxException {
        JSONObject jsonResult = new JSONObject("{}");
        HttpHeaders headers = HttpClient.createHeaders(!bodyData.isEmpty(), false);

        if (!unauthRequest) {
            headers.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + this.accessToken);
        }

        String params = HttpClient.createQuery(queryParams);
        URI uri = URIUtil.create(APIURL + endpoint + params);
        String body = HttpClient.urlencodePost(bodyData);

        HttpClientResponse response = HttpClient.request(method, uri, headers, body);

        if (response.hasJson()) {
            jsonResult = response.json();
            this.generateJSONObject(
                    jsonResult,
                    true,
                    method.name(),
                    "",
                    endpoint,
                    response.responseCode().code(),
                    null,
                    null);
        } else {
            jsonResult.put("error", response.responseBody());
            this.generateJSONObject(
                    jsonResult,
                    true,
                    method.name(),
                    "",
                    endpoint,
                    response.responseCode().code(),
                    null,
                    null);
        }

        return jsonResult;
    }

    /**
     * Method that adds extra information to our returned object.
     *
     * @param obj
     * @param isSuccess
     * @param requestType
     * @param data
     * @param url
     * @param responseCode
     * @param exception
     * @param exceptionMessage
     */
    public void generateJSONObject(JSONObject obj, boolean isSuccess,
            String requestType, String data, String url, int responseCode,
            String exception, String exceptionMessage) throws JSONException {

        obj.put("_success", isSuccess);
        obj.put("_type", requestType);
        obj.put("_post", data);
        obj.put("_url", url);
        obj.put("_http", responseCode);
        obj.put("_exception", exception);
        obj.put("_exceptionMessage", exceptionMessage);
    }

    public String GetAuthorizeURI(FullHttpRequest req) {
        String streamLabsClientId = CaselessProperties.instance().getProperty("streamlabsclientid", "");

        String host = req.headers().get(HttpHeaderNames.HOST);

        if (host == null) {
            host = "";
        } else if (HTTPWSServer.instance().isSsl()) {
            host = "https://" + host;
        } else {
            host = "http://" + host;
        }

        return "https://streamlabs.com/api/v2.0/authorize"
                + "?client_id=" + streamLabsClientId
                + "&redirect_uri=" + host + "/streamlabsoauth"
                + "&response_type=code"
                + "&scope=donations.read";
    }

    public Boolean RequestAccessToken(FullHttpRequest req, String code)
            throws JSONException, URISyntaxException, IllegalStateException {
        String host = req.headers().get(HttpHeaderNames.HOST);

        if (host == null) {
            host = "";
        } else if (HTTPWSServer.instance().isSsl()) {
            host = "https://" + host;
        } else {
            host = "http://" + host;
        }

        Map<String, String> params = new HashMap<>();
        Map<String, String> bodyData = new HashMap<>();
        bodyData.put("grant_type", "authorization_code");
        bodyData.put("client_id", CaselessProperties.instance().getProperty("streamlabsclientid", ""));
        bodyData.put("client_secret", CaselessProperties.instance().getProperty("streamlabsclientsecret", ""));
        bodyData.put("redirect_uri", host + "/streamlabsoauth");
        bodyData.put("code", code);

        JSONObject response = requestJsonResponse(HttpMethod.POST, "/token", params, bodyData, true);

        if (response.has("access_token")) {
            String accessToken = response.getString("access_token");

            Transaction transaction = CaselessProperties.instance().startTransaction(Transaction.PRIORITY_NORMAL);
            transaction.setProperty("streamlabsaccesstoken", accessToken);
            transaction.commit();

            this.SetAccessToken(accessToken);

            return true;
        }

        return false;
    }

    /*
     * Sets the Access Token to authenticate with TwitchAlerts API.
     *
     * @param accessToken
     */
    public void SetAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /*
     * Sets a new limit to how many donation records to return.
     *
     * @param donationPullLimit
     */
    public void SetDonationPullLimit(int donationPullLimit) {
        this.donationPullLimit = donationPullLimit;
    }

    /*
     * Sets a new currency code to convert all records to.
     *
     * @param currencyCode
     */
    public void SetCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    /*
     * Pulls donation information.
     *
     * @return donationsObject
     */
    public JSONObject GetDonations(int lastId) throws JSONException, URISyntaxException {
        Map<String, String> params = new HashMap<>();
        Map<String, String> bodyData = new HashMap<>();
        params.put("limit", Integer.toString(this.donationPullLimit));
        params.put("currency", this.currencyCode);

        if (lastId > 0) {
            params.put("after", Integer.toString(lastId));
        }

        return requestJsonResponse(HttpMethod.GET, "/donations", params, bodyData);
    }

    /*
     * Get an individuals points.
     *
     * @param userName User to lookup
     * 
     * @param channelName Channel name to lookup
     *
     * @return pointsObject
     */
    public JSONObject GetPointsAPI(String userName, String channelName) throws JSONException, URISyntaxException {
        Map<String, String> params = new HashMap<>();
        Map<String, String> bodyData = new HashMap<>();
        params.put("username", userName);
        params.put("channel", channelName);

        return requestJsonResponse(HttpMethod.GET, "/points", params, bodyData);
    }

    /*
     * Set points for an individual.
     *
     * @param userName User to modify
     * 
     * @param points Points to set to.
     *
     * @return pointsObject
     */
    public JSONObject SetPointsAPI(String userName, int points) throws JSONException, URISyntaxException {
        Map<String, String> params = new HashMap<>();
        Map<String, String> bodyData = new HashMap<>();
        bodyData.put("username", userName);
        bodyData.put("points", Integer.toString(points));

        return requestJsonResponse(HttpMethod.POST, "/points", params, bodyData);
    }

    /*
     * Add points to all in chat.
     *
     * @param channelName Channel name
     * 
     * @param points Points to add.
     *
     * @return pointsToAddObject
     */
    public JSONObject AddToAllPointsAPI(String channelName, int points) throws JSONException, URISyntaxException {
        Map<String, String> params = new HashMap<>();
        Map<String, String> bodyData = new HashMap<>();
        bodyData.put("channel", channelName);
        bodyData.put("value", Integer.toString(points));

        return requestJsonResponse(HttpMethod.POST, "/points/add_to_all", params, bodyData);
    }

    /*
     * Get an individuals points.
     *
     * @param userName User to lookup
     * 
     * @param channelName Channel name to lookup
     *
     * @return points (-1 on error)
     */
    public int GetPoints(String userName, String channelName) throws JSONException, URISyntaxException {
        JSONObject jsonObject = GetPointsAPI(userName, channelName);

        if (jsonObject.has("points")) {
            return jsonObject.getInt("points");
        }
        return -1;
    }

    /*
     * Set points for an individual.
     *
     * @param userName User to modify
     * 
     * @param points Points to set to.
     *
     * @return newPoints
     */
    public int SetPoints(String userName, int points) throws JSONException, URISyntaxException {
        JSONObject jsonObject = SetPointsAPI(userName, points);

        if (jsonObject.has("points")) {
            return jsonObject.getInt("points");
        }
        return -1;
    }

    /*
     * Add points to all in chat.
     *
     * @param channelName Channel name
     * 
     * @param points Points to add.
     *
     * @return boolean
     */
    public boolean AddToAllPoints(String channelName, int points) throws JSONException, URISyntaxException {
        JSONObject jsonObject = AddToAllPointsAPI(channelName, points);

        if (jsonObject.has("message")) {
            if (jsonObject.getString("message").equalsIgnoreCase("success")) {
                return true;
            }
        }
        return false;
    }
}
