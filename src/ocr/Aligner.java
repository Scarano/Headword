package ocr;

import java.io.PrintStream;

public class Aligner {
	public static interface Unit {
		public float distanceTo(Unit u);
	}
	
	static final int MIN_BEAM_WIDTH = 10;
	
	static enum Operation { MATCH, INSERT, DELETE };
	
	static class Cell {
		public Operation operation;
		public float cost;

		public Cell(Operation operation, float cost) {
			this.operation = operation;
			this.cost = cost;
		}
	}
	static class CellRow {
		final int startIndex;
		final int endIndex;
		final Cell[] cells;
		
		public CellRow(int startIndex, int endIndex) {
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			cells = new Cell[endIndex - startIndex];
		}
		
		public boolean inRange(int j) {
			return j >= startIndex && j < endIndex;
		}
		
		public Cell get(int j) {
			return cells[j - startIndex];
		}
		
		public void put(int j, Cell cell) {
			cells[j - startIndex] = cell;
		}
	}
	
	public static boolean debug = false;
	
	boolean narrowBeam;
	Unit[] startUnits;
	Unit[] endUnits;
	CellRow[] cellRows;
	
	public Aligner(Unit[] startUnits, Unit[] endUnits, boolean narrowBeam) {
		this.narrowBeam = narrowBeam;
		this.startUnits = startUnits;
		this.endUnits = endUnits;
		this.cellRows = new CellRow[startUnits.length];
	}
	
	public int[] align() {
		if (!narrowBeam || startUnits.length <= 1) {
			for (int i = 0; i < startUnits.length; i++)
				cellRows[i] = new CellRow(0, endUnits.length);
		}
		else {
			int beamWidth = Math.max(MIN_BEAM_WIDTH,
					(int) (1.5 * Math.abs(startUnits.length - endUnits.length)));
			for (int i = 0; i < startUnits.length; i++) {
				int beamStart = i * endUnits.length / startUnits.length - beamWidth / 2;
				int beamEnd = beamStart + beamWidth;
				int cellRowStart = Math.max(0, beamStart);
				int cellRowEnd = Math.min(endUnits.length, beamEnd);
				cellRows[i] = new CellRow(cellRowStart, cellRowEnd);
			}
		}
		
		for (int i = 0; i < startUnits.length; i++) {
			for (int j = cellRows[i].startIndex; j < cellRows[i].endIndex; j++) {

				Operation operation = Operation.MATCH;
				float cost = Float.MAX_VALUE;
				if (i == 0)
					cost = j + startUnits[0].distanceTo(endUnits[j]);
				else if (j == 0)
					cost = i + startUnits[i].distanceTo(endUnits[0]);
				else if (i > 0 && cellRows[i - 1].inRange(j - 1))
					cost = Math.min(cost,
							cellRows[i-1].get(j-1).cost + startUnits[i].distanceTo(endUnits[j]));
				
				if (i > 0 && cellRows[i - 1].inRange(j)) {
					float deletionCost = cellRows[i - 1].get(j).cost + 1.0F;
					if (deletionCost < cost) {
						operation = Operation.DELETE;
						cost = deletionCost;
					}
				}

				if (cellRows[i].inRange(j - 1)) {
					float insertionCost = cellRows[i].get(j - 1).cost + 1.0F;
					if (insertionCost < cost) {
						operation = Operation.INSERT;
						cost = insertionCost;
					}
				}
				
				cellRows[i].put(j, new Cell(operation, cost));
			}
		}
		
		if (debug)
			printCellTable(System.out);

		int[] alignment = new int[startUnits.length];
		int i = startUnits.length - 1;
		int j = endUnits.length - 1;
		while (i >= 0 && j >= 0) {
			Cell cell = cellRows[i].get(j);
			
			switch (cell.operation) {
			case MATCH:
				alignment[i--] = j--;
				break;
			case INSERT:
				j--;
				break;
			case DELETE:
				alignment[i--] = -1;
			}
		}
		while (i >= 0)
			alignment[i--] = -1;
		
		return alignment;
	}
	
	public void printCellTable(PrintStream out) {
		for (Unit unit: endUnits) {
			out.print("\t" + unit);
		}
		System.out.println();
		for (int i = 0; i < startUnits.length; i++) {
			out.print(startUnits[i]);
			for (int j = 0; j < endUnits.length; j++) {
				out.print("\t" + (cellRows[i].inRange(j) ? cellRows[i].get(j).cost : ""));
			}
			out.println();
		}
	}
	
}



















