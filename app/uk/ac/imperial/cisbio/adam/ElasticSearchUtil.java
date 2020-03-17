package uk.ac.imperial.cisbio.adam;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHits;

public class ElasticSearchUtil {
	private static final String index = "items";

	private Node node;

	public ElasticSearchUtil() {
		node = NodeBuilder.nodeBuilder().node();
		node.client().admin().cluster().health(new ClusterHealthRequest(index).waitForYellowStatus()).actionGet();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				node.close();
			}
		});
	}

	public void index(Item item) throws IOException, InterruptedException, ExecutionException {
		node.client()
		.prepareIndex(index, "item", item.id.toString())
		.setSource(XContentFactory.jsonBuilder()
				.startObject()
				.field("text", item.text)
				.field("title", item.title)
				.endObject()
				)
				.execute()
				.get();//should be actionGet
	}

	public SearchHits search(String term) {
		return node.client()
				.prepareSearch(index)
				//.setQuery(QueryBuilders.termQuery("text", term))
				.setQuery(QueryBuilders.queryString(term))
				.setHighlighterPreTags("<strong>")
				.setHighlighterPostTags("</strong>")
				.addHighlightedField("text")
				.addHighlightedField("title")
				.execute()
				.actionGet()
				.getHits();
	}

	public String get(String id) {
		return node.client().prepareGet(index, "item", id)
				.execute()
				.actionGet()
				.sourceAsString();
	}

	public void delete(Item item) {
		node.client().prepareDelete(index, "item", item.id.toString())
		.execute()
		.actionGet();
	}
}

