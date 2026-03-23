package com.moodtune.app.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/** Singleton Retrofit client pointing at the Jamendo v3 API. */
public class JamendoRetrofitClient {

    private static final String BASE_URL = "https://api.jamendo.com/v3.0/";

    private static JamendoApiService instance;

    public static synchronized JamendoApiService getInstance() {
        if (instance == null) {
            // Log request/response lines in debug builds (no-op in release with R8)
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)  // fail fast on slow networks
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            instance = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(JamendoApiService.class);
        }
        return instance;
    }

    private JamendoRetrofitClient() {}
}
