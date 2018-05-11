
import gnu.trove.map.hash.TLongObjectHashMap;


public class Node 
{
	boolean set = false;
	TLongObjectHashMap<TLongObjectHashMap<Node>> map = new TLongObjectHashMap<TLongObjectHashMap<Node>>();
}
