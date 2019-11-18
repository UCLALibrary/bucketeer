
package edu.ucla.library.bucketeer.verticles;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;

import javax.naming.ConfigurationException;

import com.opencsv.CSVWriter;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.utils.CodeUtils;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

public class FesterVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FesterVerticle.class, Constants.MESSAGES);

    private String myFesterHost;

    private String myFesterPath;

    private int myFesterPort;

    @Override
    public void start() throws Exception {
        super.start();

        final JsonObject config = config();
        final String urlConfigValue = config.getString(Config.FESTER_URL);

        if (urlConfigValue != null) {
            final URL festerURL = new URL(urlConfigValue);

            myFesterPort = festerURL.getPort();
            myFesterHost = festerURL.getHost();
            myFesterPath = festerURL.getPath();
        } else {
            throw new ConfigurationException(LOGGER.getMessage(MessageCodes.BUCKETEER_159));
        }

        getJsonConsumer().handler(message -> {
            final JsonObject messageBody = message.body();
            final JsonObject json = messageBody.getJsonObject(Constants.BATCH_METADATA);
            final Job job = json.mapTo(Job.class);

            try {
                final WebClient webClient = WebClient.create(vertx);
                final HttpRequest<Buffer> postRequest = webClient.post(myFesterPort, myFesterHost, myFesterPath);
                final MultipartForm form = MultipartForm.create();
                final Buffer csv = Buffer.buffer(writeToCsv(job));
                final FileSystem fileSystem = vertx.fileSystem();
                final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
                final String csvFilePath = new File(tmpDir, job.getName()).getAbsolutePath();

                fileSystem.writeFile(csvFilePath, csv, write -> {
                    if (write.succeeded()) {
                        form.textFileUpload("csv-file", job.getName(), csvFilePath, Constants.CSV);

                        postRequest.sendMultipartForm(form, sendMultipartForm -> {
                            final HttpResponse<Buffer> postResponse = sendMultipartForm.result();
                            final String postStatusMessage = postResponse.statusMessage();
                            final int postStatusCode = postResponse.statusCode();

                            if (postStatusCode == HTTP.OK) {
                                message.reply(message);
                            } else {
                                final String details = LOGGER.getMessage(MessageCodes.BUCKETEER_157, csvFilePath,
                                        postStatusCode, postStatusMessage);

                                message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_157), details);
                            }
                        });
                    } else {
                        final String details = LOGGER.getMessage(MessageCodes.BUCKETEER_158, csvFilePath);

                        LOGGER.error(write.cause(), details);
                        message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_158), details);
                    }
                });
            } catch (final IOException details) {
                LOGGER.error(details, details.getMessage());
                message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_500), details.getMessage());
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    private String writeToCsv(final Job aJob) throws IOException {
        final String[] metadataHeader = aJob.getMetadataHeader();
        final List<String[]> metadata = aJob.getMetadata();
        final StringWriter stringWriter = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(stringWriter);

        // Let's be explicit and put all values in quotes
        csvWriter.writeNext(metadataHeader, true);
        csvWriter.writeAll(metadata, true);
        csvWriter.close();

        return stringWriter.toString();
    }
}
