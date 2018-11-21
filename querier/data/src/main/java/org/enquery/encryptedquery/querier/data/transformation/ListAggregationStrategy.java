package org.enquery.encryptedquery.querier.data.transformation;

import java.util.ArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.CompletionAwareAggregationStrategy;
import org.apache.camel.util.toolbox.FlexibleAggregationStrategy;

public class ListAggregationStrategy implements AggregationStrategy {

	private CompletionAwareAggregationStrategy delegateStrategy;

	public ListAggregationStrategy() {
		delegateStrategy = new FlexibleAggregationStrategy<Object>()
				.storeInBody()
				.accumulateInCollection(ArrayList.class);
	}

	@Override
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
		return delegateStrategy.aggregate(oldExchange, newExchange);
	}

}
