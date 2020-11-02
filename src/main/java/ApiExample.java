import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.core.ApiFuture;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Subscription;
import com.google.api.services.youtube.model.SubscriptionListResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import model.YTChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;


public class ApiExample
{
    private static final String CLIENT_SECRETS = "client_secret.json";
    private static final String FIREBASE_ADMIN_SECRETS = "service-account-file.json";
    private static final Collection<String> SCOPES = Collections.singletonList( "https://www.googleapis.com/auth/youtube.readonly" );

    private static final String APPLICATION_NAME = "API code samples";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final Map<String, YTChannel> CHANNEL_MAP = new TreeMap<>();

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ApiExample.class.getName());

    /**
     * Create an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException IOException
     */
    private static Credential authorize( final NetHttpTransport httpTransport ) throws IOException
    {
        // Load client secrets.
        final InputStream clientSecretsJson = ApiExample.class.getResourceAsStream( CLIENT_SECRETS );
        final InputStreamReader inputStreamReader = new InputStreamReader( clientSecretsJson );
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load( JSON_FACTORY, inputStreamReader );
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow
                .Builder( httpTransport, JSON_FACTORY, clientSecrets, SCOPES )
                .build();

        // TODO: use constants
        LocalServerReceiver receiver = new LocalServerReceiver
                .Builder()
                .setHost( "localhost" )
                .setPort( 8080 )
                .build();

        return new AuthorizationCodeInstalledApp( flow, receiver ).authorize( "user" );
    }

    /**
     * Build and return an authorized API client service.
     *
     * @return an authorized API client service
     * @throws GeneralSecurityException, IOException
     */
    private static YouTube getService() throws GeneralSecurityException, IOException
    {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize( httpTransport );
        return new YouTube.Builder( httpTransport, JSON_FACTORY, credential )
                .setApplicationName( APPLICATION_NAME )
                .build();
    }

    /**
     * Call function to create API service object. Define and
     * execute API request. Print API response.
     *
     * @throws GeneralSecurityException, IOException, GoogleJsonResponseException
     */
    public static void main( String[] args ) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException
    {
        log.setLevel( Level.ALL );

        final Firestore firestore = connectFirebase();
        YouTube youtubeService = getService();
        // Define and execute the API request
        // TODO: use constants
        Long pageSize = 50L;

        YouTube.Subscriptions.List request = youtubeService.subscriptions().list( "snippet,contentDetails" );
        request.setMaxResults( pageSize );

        SubscriptionListResponse response = request.setMine( true ).execute();

        String pageToken = response.getNextPageToken();
        addToMap( response.getItems() );

        long pagesCount = response.getPageInfo().getTotalResults() / pageSize;
        for ( int i = 0; i < pagesCount; i++ )
        {
            request.setPageToken( pageToken );
            response = request.execute();
            pageToken = response.getNextPageToken();
            addToMap( response.getItems() );
        }
        writeChannels( firestore );
    }

    private static void addToMap( List<Subscription> subscriptions )
    {
        subscriptions.forEach( subscription ->
        {
            String channelId = subscription.getSnippet().getResourceId().getChannelId();
            YTChannel channel = YTChannel.builder()
                    .id( channelId )
                    .title( subscription.getSnippet().getTitle() )
                    .description( subscription.getSnippet().getDescription() )
                    .videosCount( subscription.getContentDetails().getTotalItemCount() )
                    .build();
            CHANNEL_MAP.put( channelId, channel );
            log.info( channelId );
            log.warning( subscription.getSnippet().getTitle() );
        } );
    }

    private static Firestore connectFirebase() throws IOException
    {
        final InputStream credentialsJson = ApiExample.class.getResourceAsStream( FIREBASE_ADMIN_SECRETS );
        final GoogleCredentials googleCredentials = GoogleCredentials.fromStream( credentialsJson );

        // TODO: use constants
        final FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials( googleCredentials )
                .setProjectId( "java-firebase-admin" )
                .setDatabaseUrl( "https://java-firebase-admin.firebaseio.com/" )
                .build();

        FirebaseApp.initializeApp( options );
        return FirestoreClient.getFirestore();
    }

    private static void writeChannels( final Firestore firestore ) throws ExecutionException, InterruptedException
    {
        WriteBatch batch = firestore.batch();
        // TODO: use constants
        CollectionReference collection = firestore.collection( "channels" );
        ApiExample.CHANNEL_MAP.forEach( ( channelId, ytChannel ) ->
        {
            log.info( channelId );
            batch.set( collection.document( channelId ), ytChannel );
        } );
        final ApiFuture<List<WriteResult>> result = batch.commit();
        result.get();
        if ( result.isDone() )
        {
            log.info( () -> CHANNEL_MAP.size() + " Channels committed." );
        } else
        {
            log.warning( () -> CHANNEL_MAP.size() + " Channels commit failed." );
        }
    }
}
