package com.unikre.pixabay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.unikre.pixabay.http.Hit;
import com.unikre.pixabay.http.Image;
import com.unikre.pixabay.http.ImageSearchRequestParams;
import com.unikre.pixabay.http.Result;
import com.unikre.pixabay.http.Video;
import com.unikre.pixabay.http.VideoSearchRequestParams;
import lombok.Getter;
import lombok.Setter;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.lang.reflect.Type;

public class PixabayClient {
    public static final int REQUEST_LIMIT_PER_HOUR = 5000;

    @Getter
    @Setter
    protected String apiKey;

    @Getter
    protected double requestsLimitIn30min;

    @Getter
    protected double remainingRequests;

    @Getter
    protected double remainingSecsToResetLimit;

    private static PixabayService pixabayService;

    private static final Gson gson = new Gson();

    private static final Type IMAGE_RESULT_TYPE = new TypeToken<Result<Image>>() {
    }.getType();

    private static final Type VIDEO_RESULT_TYPE = new TypeToken<Result<Image>>() {
    }.getType();

    public PixabayClient(String apiKey) {
        setApiKey(apiKey);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://pixabay.com")
                .build();
        pixabayService = retrofit.create(PixabayService.class);
    }

    private void parseRateLimit(Response response) {
        String value = response.headers().get("X-RateLimit-Limit");
        if (value != null && value.length() > 0) {
            requestsLimitIn30min = Double.parseDouble(value);
        }

        value = response.headers().get("X-RateLimit-Remaining");
        if (value != null && value.length() > 0) {
            remainingRequests = Double.parseDouble(value);
        }

        value = response.headers().get("X-RateLimit-Reset");
        if (value != null && value.length() > 0) {
            remainingSecsToResetLimit = Double.parseDouble(value);
        }

    }

    private void validateResponse(Response response) throws Exception {
        // Result - status code
        int statusCode = response.code();
        if (statusCode != 200) {
            throw new Exception("API call error: " + statusCode + " - " + response.message());
        }

        // Result - body
        if (response.body() == null) {
            throw new Exception("API call error: Empty response body");
        }
    }

    private <T extends Hit> Result<T> parseResponse(Response<ResponseBody> response, Type type) throws Exception {
        parseRateLimit(response);
        validateResponse(response);

        JSONObject jsonObject = new JSONObject(response.body().string());

        return gson.fromJson(jsonObject.toString(), type);
    }


    private Call<ResponseBody> imageSearchRequestToCall(ImageSearchRequestParams params) {
        return pixabayService.searchImages(params.getKey(),
                params.getQ(),
                params.getLang(),
                params.getId(),
                params.getCategory(),
                params.getMinWidth(),
                params.getMinHeight(),
                params.getEditorsChoice(),
                params.getSafeSearch(),
                params.getOrder(),
                params.getPage(),
                params.getPerPage(),
                params.getPretty(),
                params.getResponseGroup(),
                params.getImageType(),
                params.getOrientation());
    }

    /**
     * Image search
     **/
    public Result<Image> searchImage(ImageSearchRequestParams params) throws Exception {
        Response<ResponseBody> response = imageSearchRequestToCall(params).execute();

        return parseResponse(response, IMAGE_RESULT_TYPE);
    }

    public Result<Image> searchImage(String q) throws Exception {
        ImageSearchRequestParams params = ImageSearchRequestParams.builder()
                .key(apiKey)
                .q(q)
                .build();

        return searchImage(params);
    }

    private <T extends Hit> void enqueueCall(Call<ResponseBody> call, final PixabayCallback<Result<T>> callback, final Type type) {
        Callback<ResponseBody> genericCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    final Result<T> result = parseResponse(response, type);
                    callback.onResponse(result);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        };

        call.enqueue(genericCallback);
    }

    public void searchImage(ImageSearchRequestParams params, final PixabayCallback<Result<Image>> callback) {
        Call<ResponseBody> call = imageSearchRequestToCall(params);
        enqueueCall(call, callback, IMAGE_RESULT_TYPE);
    }

    public void searchImage(String q, PixabayCallback<Result<Image>> callback) {
        ImageSearchRequestParams params = ImageSearchRequestParams.builder()
                .key(apiKey)
                .q(q)
                .build();

        searchImage(params, callback);
    }

    private Call<ResponseBody> videoSearchRequestParamsToCall(VideoSearchRequestParams params) {
        return pixabayService.searchVideos(params.getKey(),
                params.getQ(),
                params.getLang(),
                params.getId(),
                params.getCategory(),
                params.getMinWidth(),
                params.getMinHeight(),
                params.getEditorsChoice(),
                params.getSafeSearch(),
                params.getOrder(),
                params.getPage(),
                params.getPerPage(),
                params.getPretty(),
                params.getVideoType());

    }

    /**
     * Video search
     **/
    public Result<Video> searchVideo(VideoSearchRequestParams params) throws Exception {
        Call<ResponseBody> call = videoSearchRequestParamsToCall(params);

        Response<ResponseBody> response = call.execute();

        JSONObject jsonObject = new JSONObject(response.body().string());

        Type collectionType = new TypeToken<Result<Video>>() {
        }.getType();
        return gson.fromJson(jsonObject.toString(), collectionType);
    }

    public Result<Video> searchVideo(String q) throws Exception {
        VideoSearchRequestParams params = VideoSearchRequestParams.builder()
                .key(apiKey)
                .q(q)
                .build();
        return searchVideo(params);
    }

    public void searchVideo(VideoSearchRequestParams params, final PixabayCallback<Result<Video>> callback) {
        Call<ResponseBody> call = videoSearchRequestParamsToCall(params);
        Callback<ResponseBody> genericCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    Result<Video> result = parseResponse(response, VIDEO_RESULT_TYPE);
                    callback.onResponse(result);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        };

        call.enqueue(genericCallback);
    }

    public void searchVideo(String q, PixabayCallback<Result<Video>> callback) {
        VideoSearchRequestParams params = VideoSearchRequestParams.builder()
                .key(apiKey)
                .q(q)
                .build();

        searchVideo(params, callback);
    }
}
