
import java.io.Serializable;

 class Pair implements Serializable
 {
	long number;
	long set;
	
	public Pair(final long number, final long set)
	{
		this.number = number;
		this.set = set;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (number ^ (number >>> 32));
		result = prime * result + (int) (set ^ (set >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pair other = (Pair) obj;
		if (number != other.number)
			return false;
		if (set != other.set)
			return false;
		return true;
	}

		
}
