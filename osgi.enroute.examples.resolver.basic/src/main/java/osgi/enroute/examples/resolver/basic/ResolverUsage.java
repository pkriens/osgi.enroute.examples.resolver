package osgi.enroute.examples.resolver.basic;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.osgi.framework.namespace.BundleNamespace.BUNDLE_NAMESPACE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.felix.resolver.Logger;
import org.apache.felix.resolver.ResolverImpl;
import org.junit.Test;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.repository.Repository;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

import aQute.bnd.build.model.EE;
import aQute.bnd.osgi.repository.ResourcesRepository;
import aQute.bnd.osgi.repository.XMLResourceParser;
import aQute.bnd.osgi.resource.FilterBuilder;
import aQute.bnd.osgi.resource.RequirementBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;

/**
 * This class shows how to use the OSGi Resolver
 *
 */
public class ResolverUsage {
	Visualizer viz = new Visualizer();
	
	/**
	 * Example (simple) usage of the resolver
	 * 
	 */
	@Test
	public void resolve() throws Exception {

		ResourcesRepository repo = getRepository("https://raw.githubusercontent.com/osgi/osgi.enroute/v1.0.0/cnf/distro/index.xml");
		Resource system = getSystemResource(repo, EE.JavaSE_1_8, "org.eclipse.osgi");
		RequirementBuilder initialRequirementBuilder = getInitialRequirements("org.apache.felix.http.jetty");

		ResolveContext context = new Context(repo, system,
				singleton(initialRequirementBuilder.buildSyntheticRequirement()));
		
		Resolver r = getResolver();
		
		Map<Resource, List<Wire>> resolve = r.resolve(context);

		viz.print(resolve);
		viz.show(resolve);
	}

	private Resolver getResolver() {
		Logger l = new Logger(0);
		Resolver r = new ResolverImpl(l);
		return r;
	}

	/**
	 * Provides a very simplistic implementation of the ResolveContext. This class will become
	 * more complicated for industrial resolvers.
	 */
	static class Context extends ResolveContext {

		final Repository		repo;
		final Set<Requirement>	initialRequirements;
		final Resource			system;

		public Context(Repository repo, Resource system, Set<Requirement> initialRequirements) {
			this.repo = repo;
			this.system = system;
			this.initialRequirements = initialRequirements;
		}

		@Override
		public List<Capability> findProviders(Requirement requirement) {
			List<Capability> systemCapabilities = ResourceUtils.findProviders(requirement,
					system.getCapabilities(null));
			if (systemCapabilities != null && !systemCapabilities.isEmpty())
				return systemCapabilities;

			return repo.findProviders(Collections.singleton(requirement)).//
					values().//
					stream().//
					flatMap(list -> list.stream()).//
					collect(toList());
		}

		@Override
		public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
			int newIndex = capabilities.size();
			capabilities.add(newIndex, hostedCapability);
			return newIndex;
		}

		@Override
		public boolean isEffective(Requirement requirement) {
			return true;
		}

		@Override
		public Map<Resource, Wiring> getWirings() {
			return Collections.emptyMap();
		}

		@Override
		public Collection<Resource> getMandatoryResources() {
			Map<Requirement, Collection<Capability>> providers = repo.findProviders(initialRequirements);
			Set<Resource> resources = ResourceUtils
					.getResources(providers.values().stream().flatMap(l -> l.stream()).collect(Collectors.toList()));

			return resources;
		}
	}


	/*
	 * Calculate the initial requirements
	 */
	private RequirementBuilder getInitialRequirements(String bsn) {
		RequirementBuilder initialRequirementBuilder = new RequirementBuilder(BUNDLE_NAMESPACE);
		FilterBuilder filterBuilder = new FilterBuilder();
		filterBuilder.eq(BUNDLE_NAMESPACE, bsn);
		initialRequirementBuilder.addFilter(filterBuilder);
		return initialRequirementBuilder;
	}

	/*
	 * Calculate the system resource
	 */
	private Resource getSystemResource(ResourcesRepository repo, EE ee, String framework) throws Exception, IOException {
		ResourceBuilder system = new ResourceBuilder();
		getExecutionEnvironnment(system, ee);
		getFramework(repo, system, framework);
		return system.build();
	}
	
	/*
	 * Load the framework resource
	 */
	private void getFramework(ResourcesRepository repo, ResourceBuilder system, String frameworkBsn) throws Exception {
		List<Capability> framework = repo.findProvider(RequirementBuilder
				.createBundleRequirement(frameworkBsn, null).buildSyntheticRequirement());
		Set<Resource> frameworkResource = ResourceUtils.getResources(framework);
		system.addCapabilities(
				frameworkResource.
				stream().
				flatMap( r -> r.getCapabilities(null).
						stream()).
				collect(Collectors.toList()));
	}

	/*
	 * Calculate the execution environment
	 */
	private void getExecutionEnvironnment(ResourceBuilder system, EE ee) throws Exception, IOException {
		system.addAllExecutionEnvironments(ee);
		system.addExportPackages(ee.getPackages());
	}

	/*
	 * Get the repository
	 */
	private ResourcesRepository getRepository(String uri) throws Exception, URISyntaxException {
		List<Resource> resources = XMLResourceParser.getResources(
				new URI(uri));
		ResourcesRepository repo = new ResourcesRepository(resources);
		return repo;
	}
}
