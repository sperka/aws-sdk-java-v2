package software.amazon.awssdk.metrics.publishers.cloudwatch.internal.task;

import static software.amazon.awssdk.metrics.publishers.cloudwatch.internal.CloudWatchMetricLogger.METRIC_LOGGER;

import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.metrics.publishers.cloudwatch.internal.MetricUploader;
import software.amazon.awssdk.metrics.publishers.cloudwatch.internal.transform.MetricCollectionAggregator;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

public class FlushMetricsTask implements MetricTask {
    private final MetricCollectionAggregator collectionAggregator;
    private final MetricUploader uploader;
    private int maximumRequestsPerFlush;

    public FlushMetricsTask(MetricCollectionAggregator collectionAggregator,
                            MetricUploader uploader,
                            int maximumRequestsPerFlush) {
        this.collectionAggregator = collectionAggregator;
        this.uploader = uploader;
        this.maximumRequestsPerFlush = maximumRequestsPerFlush;
    }

    @Override
    public void run() {
        List<PutMetricDataRequest> allRequests = collectionAggregator.getRequests();
        List<PutMetricDataRequest> requests = allRequests;
        if (requests.size() > maximumRequestsPerFlush) {
            METRIC_LOGGER.warn(() -> "Maximum AWS SDK client-side metric call count exceeded: " + allRequests.size() +
                                     " > " + maximumRequestsPerFlush + ". Some metric requests will be dropped. This occurs when "
                                     + "the caller has configured too many metrics or too unique of dimensions without an "
                                     + "associated increase in the maximum-calls-per-upload configured on the publisher.");

            // Randomly pick which requests we intend to keep/drop so that we aren't always dropping the same ones if this happens
            // a lot of times in a row.
            Collections.shuffle(requests);
            requests = requests.subList(0, maximumRequestsPerFlush);

        }

        uploader.upload(requests);
    }
}