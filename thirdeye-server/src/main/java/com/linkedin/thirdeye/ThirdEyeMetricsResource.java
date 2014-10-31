package com.linkedin.thirdeye;

import com.codahale.metrics.annotation.Timed;
import com.linkedin.thirdeye.api.StarTree;
import com.linkedin.thirdeye.api.StarTreeConstants;
import com.linkedin.thirdeye.api.StarTreeManager;
import com.linkedin.thirdeye.api.StarTreeQuery;
import com.linkedin.thirdeye.api.StarTreeRecord;
import com.linkedin.thirdeye.impl.StarTreeQueryImpl;
import com.linkedin.thirdeye.impl.StarTreeRecordImpl;
import com.linkedin.thirdeye.impl.StarTreeUtils;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Path("/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class ThirdEyeMetricsResource
{
  private final StarTreeManager starTreeManager;
  private final ExecutorService executorService;

  public ThirdEyeMetricsResource(StarTreeManager starTreeManager, ExecutorService executorService)
  {
    this.starTreeManager = starTreeManager;
    this.executorService = executorService;
  }

  @GET
  @Path("/{collection}")
  @Timed
  public List<ThirdEyeMetricsResult> getMetrics(@PathParam("collection") String collection, @Context UriInfo uriInfo)
  {
    final StarTree starTree = starTreeManager.getStarTree(collection);
    if (starTree == null)
    {
      throw new IllegalArgumentException("No collection " + collection);
    }

    StarTreeQueryImpl.Builder queryBuilder = new StarTreeQueryImpl.Builder();

    // Dimension values
    for (String dimensionName : starTree.getConfig().getDimensionNames())
    {
      String dimensionValue = uriInfo.getQueryParameters().getFirst(dimensionName);
      if (dimensionValue == null)
      {
        dimensionValue = StarTreeConstants.STAR;
      }
      queryBuilder.setDimensionValue(dimensionName, dimensionValue);
    }

    // Between, if any
    String betweenClause = uriInfo.getQueryParameters().getFirst(ThirdEyeApplication.BETWEEN);
    if (betweenClause != null)
    {
      String[] tokens = betweenClause.split(ThirdEyeApplication.TIME_SEPARATOR);
      if (tokens.length != 2)
      {
        throw new IllegalArgumentException("BETWEEN must be specified as start,end");
      }
      queryBuilder.setTimeRange(Long.valueOf(tokens[0]), Long.valueOf(tokens[1]));
    }

    // In, if any
    String inClause = uriInfo.getQueryParameters().getFirst(ThirdEyeApplication.IN);
    if (inClause != null)
    {
      String[] tokens = inClause.split(ThirdEyeApplication.TIME_SEPARATOR);
      Set<Long> inSet = new HashSet<Long>();
      for (String token : tokens)
      {
        inSet.add(Long.valueOf(token));
      }
      queryBuilder.setTimeBuckets(inSet);
    }

    // Generate queries
    List<StarTreeQuery> queries = StarTreeUtils.expandQueries(starTree, queryBuilder.build());

    // Query tree
    Set<Future<StarTreeRecord>> results = new HashSet<Future<StarTreeRecord>>();
    for (final StarTreeQuery query : queries)
    {
      results.add(executorService.submit(new Callable<StarTreeRecord>()
      {
        @Override
        public StarTreeRecord call() throws Exception
        {
          return starTree.search(query);
        }
      }));
    }

    // Compose response
    List<ThirdEyeMetricsResult> metricsResults = new ArrayList<ThirdEyeMetricsResult>();
    for (Future<StarTreeRecord> result : results)
    {
      try
      {
        ThirdEyeMetricsResult metricsResult = new ThirdEyeMetricsResult();
        metricsResult.setDimensionValues(result.get().getDimensionValues());
        metricsResult.setMetricValues(result.get().getMetricValues());
        metricsResults.add(metricsResult);
      }
      catch (Exception e)
      {
        throw new IllegalStateException(e);
      }
    }
    return metricsResults;
  }

  @POST
  @Timed
  public Response postMetrics(ThirdEyeMetricsPayload payload)
  {
    StarTreeRecord record = new StarTreeRecordImpl.Builder()
            .setDimensionValues(payload.getDimensionValues())
            .setMetricValues(payload.getMetricValues())
            .setTime(payload.getTime())
            .build();

    StarTree starTree = starTreeManager.getStarTree(payload.getCollection());
    if (starTree == null)
    {
      throw new IllegalArgumentException("No collection " + payload.getCollection());
    }

    starTree.add(record);

    return Response.ok().build();
  }
}
