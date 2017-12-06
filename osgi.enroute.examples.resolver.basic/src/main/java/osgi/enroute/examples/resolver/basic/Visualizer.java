package osgi.enroute.examples.resolver.basic;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.osgi.dto.DTO;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

import aQute.lib.io.IO;
import aQute.lib.json.JSONCodec;

public class Visualizer {

	final Map<Object, Integer> identity = new IdentityHashMap<>();

	enum Type {
		RESOURCE, CAPABILITY, REQUIREMENT
	};

	int id = 1000;

	public static class NodeDTO extends DTO {
		public String	id;
		public String	name;
		public String	description;
		public Type		type;
	}

	public static class LinkDTO extends DTO {
		public int source, target;
	}

	public static class GraphDTO extends DTO {
		public List<NodeDTO>	nodes	= new ArrayList<>();
		public List<LinkDTO>	links	= new ArrayList<>();
	}

	public void show(Map<Resource, List<Wire>> resolve) throws Exception {
		GraphDTO gdto = new GraphDTO();

		NodeDTO sdto = new NodeDTO();
		sdto.name = "SYSTEM";
		sdto.description = sdto.name;
		sdto.type = Type.RESOURCE;

		for (Entry<Resource, List<Wire>> e : resolve.entrySet()) {

			Resource r = e.getKey();
			List<Wire> wires = e.getValue();

			NodeDTO dto = new NodeDTO();
			dto.id = id(r);
			dto.name = r.toString();
			dto.description = dto.name;
			dto.type = Type.RESOURCE;
			int resourceIndex = gdto.nodes.size();

			gdto.nodes.add(dto);

			for (Capability c : r.getCapabilities(null)) {
				NodeDTO cdto = new NodeDTO();
				cdto.id = id(c);
				cdto.name = c.toString();
				cdto.description = dto.name;
				cdto.type = Type.CAPABILITY;
				int capabilityIndex = gdto.nodes.size();
				identity.put(c, capabilityIndex);
				gdto.nodes.add(cdto);

				LinkDTO link = new LinkDTO();
				link.target = resourceIndex;
				link.source = capabilityIndex;
				gdto.links.add(link);
			}
			for (Requirement c : r.getRequirements(null)) {
				NodeDTO rdto = new NodeDTO();
				rdto.id = id(c);
				rdto.name = c.toString();
				rdto.description = dto.name;
				rdto.type = Type.REQUIREMENT;
				int requirementIndex = gdto.nodes.size();
				identity.put(c, requirementIndex);
				gdto.nodes.add(rdto);

				LinkDTO link = new LinkDTO();
				link.target = resourceIndex;
				link.source = requirementIndex;
				gdto.links.add(link);
			}

			for (Wire wire : wires) {
				LinkDTO link = new LinkDTO();
				Requirement requirement = wire.getRequirement();
				Capability capability = wire.getCapability();
				if (requirement == null || capability == null)
					continue;

				link.target = identity.getOrDefault(capability, 0);
				link.source = identity.getOrDefault(requirement, 1);
				gdto.links.add(link);
			}
		}
		File f = new File("graph.html");
		String template = IO.collect(getClass().getResource("/template.html"));
		String data = new JSONCodec().enc().writeDefaults().put(gdto).toString();
		String content = template.replace("{{DATA}}", data);
		IO.store(content, f);
		// java.awt.Desktop.getDesktop().browse( f.toURI());
	}

	private String id(Object c) {
		return "" + identity.computeIfAbsent(c, k -> id++);
	}

	public void print(Map<Resource, List<Wire>> resolve) {

		List<Wire> wires = resolve.values().stream().flatMap(list -> list.stream()).collect(Collectors.toList());

		for (Entry<Resource, List<Wire>> e : resolve.entrySet()) {
			System.out.printf("# %s\n\n", e.getKey());
			for (Wire wire : wires) {
				if (wire.getProvider() == e.getKey())
					System.out.printf("    %-60s <- %s\n", wire.getRequirer(), wire.getCapability());
			}
			System.out.printf("\n");
			for (Wire wire : wires) {
				if (wire.getRequirer() == e.getKey())
					System.out.printf("    %-60s -> %s\n", wire.getRequirement(), wire.getProvider());
			}
			System.out.printf("\n");
		}

	}
}
