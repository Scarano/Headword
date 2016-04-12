package ocr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public interface ConditionalModel<E, C> {
	
	public double prob(C context, E event);
	public double prob(C context, E event, double bgProb);
	
	static final boolean DEBUG = false;
	
	static class Observation<C, E> {
		public C context;
		public E event;
		
		public Observation(C context, E event) {
			this.context = context;
			this.event = event;
		}
		
		public C getContext() {
			return context;
		}
		
		public E getEvent() {
			return event;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null)
				return false;
			if (! (o instanceof Observation<?, ?>))
				return false;
			Observation<?, ?> obs = (Observation<?, ?>) o;
			return context.equals(obs.context) && event.equals(obs.event);
		}
		
		@Override
		public int hashCode() {
			return context.hashCode() + 31 * event.hashCode();
		}

		@Override
		public String toString() {
			return context.toString() + " " + event.toString();
		}
	}

	static class UnsmoothedConditionalModel<E, C>
		implements ConditionalModel<E, C>
	{
		HashMap<Observation<C, E>, Double> probs;
		
		public UnsmoothedConditionalModel(Observer<E, C> observer) {
			probs = new HashMap<Observation<C, E>, Double>();
			for (Map.Entry<Observation<C, E>, Counter.Count> entry: observer.jointCounter) {
				Observation<C, E> observation = entry.getKey();
				double count = entry.getValue().value;
				double contextCount = observer.contextCounter.get(observation.getContext());
				probs.put(observation, count / contextCount);
			}
		}
	
		@Override
		public double prob(C context, E event, double bgProb) {
			return prob(context, event);
		}
		
		@Override
		public double prob(C context, E event) {
			Double p = probs.get(new Observation<C, E>(context, event));
			if (p != null)
				return p;
			else
				return 0.0;
		}
		
		@Override
		public String toString() {
			String s = "";
			for (Map.Entry<Observation<C, E>, Double> entry: probs.entrySet()) {
				s += String.format("p(%s | %s) = %f\n",
						entry.getKey().getEvent(),
						entry.getKey().getContext(),
						entry.getValue());
			}
			return s;
		}
	}
	
	static class AdditiveConditionalModel<E, C>
		implements ConditionalModel<E, C>
	{
		double alpha;
		double numEvents;
		HashMap<Observation<C, E>, Double> obsCounts;
		HashMap<C, Double> contextCounts;

		public AdditiveConditionalModel(Observer<E, C> observer, double alpha) {
			this(observer, alpha, observer.priorCounter.events().size());
		}
		public AdditiveConditionalModel(Observer<E, C> observer, double alpha, int numEvents) {
			this.alpha = alpha;
			this.numEvents = (double) numEvents;
			this.obsCounts = observer.jointCounter.asMap();
			this.contextCounts = observer.contextCounter.asMap();
		}

		@Override
		public double prob(C context, E event, double bgProb) {
			return prob(context, event);
		}
		
		@Override
		public double prob(C context, E event) {
			Double cCount = contextCounts.get(context);
			if (cCount == null) cCount = 0.0;
			Double oCount = obsCounts.get(new Observation<C, E>(context, event));
			if (oCount == null) oCount = 0.0;
			double p = (oCount + alpha) / (cCount + alpha * numEvents);
			
			if (DEBUG) {
				System.out.printf("p(%s | %s) = (%f[%f] + %f) / (%f + %f * %f) = %f\n",
						event, context,
						oCount, Math.log(oCount), alpha,
						cCount, alpha, numEvents,
						p);
			}
			
			return p;
		}
		
		@Override
		public String toString() {
			String s = 
					String.format("AdditiveConditionalModel (|E| = %f, \u03b1 = %f):\n",
					numEvents, alpha);
			for (Map.Entry<Observation<C, E>, Double> entry: obsCounts.entrySet()) {
				C context = entry.getKey().getContext();
				E event = entry.getKey().getEvent();
				s += String.format("p(%s | %s) = (%f + \u03b1) / (%f + |E|\u03b1) = %f\n",
						event,
						context,
						entry.getValue(),
						contextCounts.get(context),
						prob(context, event));
			}
			return s;
		}
	}
	
	static class WBConditionalModel<E, C>
		implements ConditionalModel<E, C>
	{
		HashMap<E, Double> priorCounts;
		HashMap<Observation<C, E>, Double> obsCounts;
		HashMap<C, Double> contextCounts;
		HashMap<C, Double> lambdas;
		double totalObservations;
		double unkProb;
	
		public WBConditionalModel(Observer<E, C> observer, double unkProb) {
			priorCounts = observer.priorCounter.asMap();
			obsCounts = observer.jointCounter.asMap();
			contextCounts = observer.contextCounter.asMap();
			totalObservations = observer.totalObservations;
			this.unkProb = unkProb;
			
			HashMap<C, HashSet<E>> eventsByContext = 
					new HashMap<C, HashSet<E>>();
			for (Map.Entry<Observation<C, E>, Double> entry: obsCounts.entrySet()) {
				Observation<C, E> observation = entry.getKey();
				HashSet<E> events = eventsByContext.get(observation.getContext());
				if (events == null) {
					events = new HashSet<E>();
					eventsByContext.put(observation.getContext(), events);
				}
				events.add(observation.getEvent());
			}
			lambdas = new HashMap<C, Double>();
			for (Map.Entry<C, Double> entry: contextCounts.entrySet()) {
				double N = entry.getValue();
				double V = eventsByContext.get(entry.getKey()).size();
				lambdas.put(entry.getKey(), N / (N + V));
			}
		}
	
		@Override
		public double prob(C context, E event) {
			Double eCount = priorCounts.get(event);
			if (eCount == null) eCount = 0.0;
			return prob(context, event, eCount / totalObservations);
		}
		
		@Override
		public double prob(C context, E event, double bgProb) {
			Double oCount = obsCounts.get(new Observation<C, E>(context, event));
			if (oCount == null) oCount = 0.0;
			Double cCount = contextCounts.get(context);
			if (cCount == null) cCount = 0.0;
			
			Double lambda = lambdas.get(context);
			if (lambda == null) lambda = 0.0;
			
			double p = (1 - lambda) * bgProb;
			if (p == 0.0) {
				p = unkProb;
			}
			if (oCount > 0.0)
				p += lambda * oCount / cCount;
			
			if (DEBUG) {
				System.out.printf("p(%s | %s) = %f(%f/%f) + %f(%f) = %f\n",
						event, context,
						lambda, oCount, cCount,
						1-lambda, bgProb,
						p);
			}
			
			return p;
		}
	}
	
	
	static class Observer<E, C> {
		double totalObservations = 0.0;
		Counter<E> priorCounter = new Counter<E>();
		Counter<Observation<C, E>> jointCounter = new Counter<Observation<C, E>>();
		Counter<C> contextCounter = new Counter<C>();
		
		public void observe(C context, E event) {
			observe(context, event, 1.0);
		}
		public void observe(C context, E event, double count) {
			totalObservations += count;
			priorCounter.add(event, count);
			jointCounter.add(new Observation<C, E>(context, event), count);
			contextCounter.add(context, count);
		}
	}

}













