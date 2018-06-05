package com.redhat.coolstore.gateway.api;

import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.redhat.coolstore.gateway.model.Product;
import com.redhat.coolstore.gateway.proxy.ProductResource;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("cdi-ambiguous-dependency")
@RequestScoped
public class ProductGateway {

	@Inject
	@ConfigProperty(name = "CATALOG_SERVICE_URL", defaultValue = "http://catalog-service-coolstore-catalog.192.168.99.100.nip.io:80")
	private String catalogServiceUrl;

	@Inject
	@ConfigProperty(name = "hystrix.products.executionTimeout", defaultValue="2000")
	private int hystrixProductsExecutionTimeout;

	@Inject
	@ConfigProperty(name = "hystrix.products.groupKey", defaultValue ="product")
	private String hystrixProductsGroupKey;

	@Inject
	@ConfigProperty(name = "hystrix.products.circuitBreakerEnabled", defaultValue="true")
	private boolean hystrixProductsCircuitBreakerEnabled;

	@Inject
	@ConfigProperty(name = "hystrix.inventory.executionTimeout", defaultValue="1000")
	private int hystrixInventoryExecutionTimeout;

	@Inject
	@ConfigProperty(name = "hystrix.inventory.groupKey", defaultValue="inventory")
	private String hystrixInventoryGroupKey;

	@Inject
	@ConfigProperty(name = "hystrix.inventory.circuitBreakerEnabled", defaultValue="true")
	private boolean hystrixInventoryCircuitBreakerEnabled;

	private ProductResource buildClient() {
		Client client = ClientBuilder.newClient();
		try {
			ProductResource productResource = new GetWebTarget(client).execute();
			if (productResource == null) {
				throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
			} else {
				return productResource;
			}

		}  catch (HystrixRuntimeException e) {
			throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
		}

	}

	@GET
	@Path("/products")
	public List<Product> getProducts() {
		ProductResource proxy = buildClient();
		return proxy.getProducts();
	}

	@GET
	@Path("/product/{itemId}")
	public Product getProduct(@PathParam("itemId") String itemId) {
		ProductResource proxy = buildClient();
		return proxy.getProduct(itemId);
	}

	@POST
	@Path("/product")
	public void addProduct(final Product product) {
		ProductResource proxy = buildClient();
		proxy.addProduct(product);
	}

	
	public class GetWebTarget extends HystrixCommand<ProductResource> {
		private Client client;

		public GetWebTarget(Client client) {
			super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixProductsGroupKey))
					.andCommandPropertiesDefaults(
							HystrixCommandProperties.Setter().withCircuitBreakerEnabled(hystrixProductsCircuitBreakerEnabled)
									.withExecutionTimeoutInMilliseconds(hystrixProductsExecutionTimeout)));
			this.client = client;

		}

		@Override
		protected ProductResource run() throws Exception {
			WebTarget target = client.target(catalogServiceUrl);
			ResteasyWebTarget restEasyTarget = (ResteasyWebTarget) target;
			return restEasyTarget.proxy(ProductResource.class);
		}
	}

}
