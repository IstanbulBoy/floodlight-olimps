package net.floodlightcontroller.cli.utils;

/*
* Copyright (c) 2013, California Institute of Technology
* ALL RIGHTS RESERVED.
* Based on Government Sponsored Research DE-SC0007346
* Author Michael Bredel <michael.bredel@cern.ch>
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*     http://www.apache.org/licenses/LICENSE-2.0
* 
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
* BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
* OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
* AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
* LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
* WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
* 
* Neither the name of the California Institute of Technology
* (Caltech) nor the names of its contributors may be used to endorse
* or promote products derived from this software without specific prior
* written permission.
*/

import java.util.LinkedList;
import java.util.List;

/**
 * Represents information as strings in a table. The table can be
 * printed as a formated string in the form:
 * 
 *   [offset] header1 header2 header3 header4
 *   [offset] -------|-------|-------|-------
 *   [offset] data    data    data    data
 *   [offset] data    data    data    data 
 *   [offset] data    data    data    data   
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class StringTable {
	/** List containing all header entries. */
	private List<String> header;
	/** List containing all footer entries. */
	private List<String> footer;
	/** List containing all rows or the table. */
	private List<List<String>> table;
	/** Number of columns in the table. */
	private int columns;
	/** Offset to have some free space at the beginning of the table. */
	private int offset;
	/** */
	private boolean seperator;
	
	/**
	 * Constructor
	 */
	public StringTable(boolean seperator) {
		this.seperator = seperator;
		this.columns = 0;
		this.offset = 0;
		this.header = new LinkedList<String>();
		this.footer = new LinkedList<String>();
		this.table  = new LinkedList<List<String>>();
	}
	
	/**
	 * Convenient constructor to create a table with a seperator.
	 */
	public StringTable() {
		this(true);
	}
	
	/**
	 * Getter for the number of rows in the table.
	 * 
	 * @return The number of rows in the table.
	 */
	public int getRowLength() {
		return this.table.size();
	}
	
	/**
	 * Getter for the number of columns in the table.
	 * 
	 * @return The number of columns in the table.
	 */
	public int getColumnLength() {
		return this.columns;
	}
	
	/**
	 * Getter for a specific row in the table.
	 * 
	 * @param index The index of the table row.
	 * @return A specific table row. 
	 */
	public List<String> getRow(int index) {
		return this.table.get(index);
	}
	
	/**
	 * Getter for a specific column in the table.
	 * 
	 * @param index The index of the table column.
	 * @return A specific table column.
	 */
	public List<String> getColumn(int index) {
		/* A list of entries in a table column. */
		List<String> column = new LinkedList<String>();
		
		// Populate the column list.
		for (List<String> row : table) {
			column.add(row.get(index));
		}
		
		// Return.
		return column;
	}
	
	/**
	 * Setter for the header of the table.
	 * 
	 * @param header List containing all header entries.
	 */
	public void setHeader(List<String> header) {
		this.header = header;
		this.columns = Math.max(this.columns, header.size());
	}
	
	/**
	 * Adds a row to the table.
	 * 
	 * @param row The list of entries that is added to the table.
	 * @throws IndexOutOfBoundsException Throws exception if the row size does not match the header size.
	 */
	public void addRow(List<String> row) throws IndexOutOfBoundsException {
		if (this.header != null && !this.header.isEmpty()) {
			if (this.header.size() != row.size()) {
				// Error.
				throw new IndexOutOfBoundsException();
			}
		}
		this.table.add(row);
		this.columns = Math.max(this.columns, row.size());
	}
	
	/**
	 * Setter for the footer of the table.
	 * 
	 * @param header List containing all footer entries.
	 * @throws IndexOutOfBoundsException Throws exception if the row size does not match the header size.
	 */
	public void setFooter(List<String> footer) throws IndexOutOfBoundsException {
		if (this.header != null && !this.header.isEmpty()) {
			if (this.header.size() != footer.size()) {
				// Error.
				throw new IndexOutOfBoundsException();
			}
		}
		this.footer = footer;
		this.columns = Math.max(this.columns, footer.size());
	}
	
	/**
	 * Setter for an offset.
	 * 
	 * @param offset Number of blanks before each row of the table.
	 */
	public StringTable setOffset(int offset) {
		this.offset = offset;
		return this;
	}
	
	@Override
	public String toString() {
		/* String builder that contains a representation of the table content. */
		StringBuilder stringBuilder = new StringBuilder();
		/* Format string to format the table rows. */
		String formatString = this.generateFormatString();
		
		// Add header if available.
		if (!this.header.isEmpty())
			stringBuilder.append(String.format(formatString, this.header.toArray()));
		
		// Add separator.
		if (this.seperator)
			stringBuilder.append(this.generateSeparator());
		
		// Add table contend.
		for (List<String> row : this.table) {
			stringBuilder.append(String.format(formatString, row.toArray()));
		}
		
		// Add footer if available.
		if (!this.footer.isEmpty()) {
			stringBuilder.append(this.generateSeparator());
			stringBuilder.append(String.format(formatString, this.footer.toArray()));
		}
		
		// Return.
		return stringBuilder.toString();
	}
	
	/**
	 * Generates a separator to separate the header and the footer
	 * (if available) from the table content. The separator looks
	 * like:
	 * 
	 *   [offset] -----|--------|-----|------
	 *   
	 * and is adapted to the column width that equals the length
	 * of the longest string the the column.
	 * 
	 * @return separator string
	 */
	private String generateSeparator() {
		/* String builder to generate the separation string. */
		StringBuilder stringBuilder = new StringBuilder();
		
		// Add offset blanks.
		for (int index=0; index<this.offset; index++) {
			stringBuilder.append(" ");
		}

		// Generate the separation string.
		for (int index=0; index<this.columns; index++) {
			for (int j=0; j<this.getMaxStringLength(index); j++) {
				stringBuilder.append("-");
			}
			stringBuilder.append("|");
		}
		
		// Replace the last character by CR.
		int length = stringBuilder.length();
		if (length > 0)
			stringBuilder.replace(length-1, length, "\n");
		
		// Return.
		return stringBuilder.toString();
	}
	
	/**
	 * Generates a format string to format the rows of the 
	 * table. The format string looks like:
	 * 
	 *   %1$-23s|%2$-12s|%3$-6s|%4$-11s|%5$-23s\n";
	 *   
	 * and is adapted to the column width that equals the length
	 * of the longest string the the column.
	 * 
	 * @return format string.
	 */
	private String generateFormatString() {
		/* String builder to generate the separation string. */
		StringBuilder stringBuilder = new StringBuilder();
		
		// Add offset blanks.
		for (int index=0; index<this.offset; index++) {
			stringBuilder.append(" ");
		}
		
		// Generate the format string.
		for (int index = 0; index < this.columns; index++) {
			stringBuilder.append("%");
			stringBuilder.append(index+1);
			stringBuilder.append("$-");
			stringBuilder.append(this.getMaxStringLength(index));
			stringBuilder.append("s ");
		}
		
		// Replace the last character by CR.
		int length = stringBuilder.length();
		if (length > 0)
			stringBuilder.replace(length-1, length, "\n");

		// Return.
		return stringBuilder.toString();
	}
	
	/**
	 * Gets the maximum length over all strings stored in
	 * a column.
	 * 
	 * @param index The index of the column.
	 * @return the maximum length of all strings stored in the columm.
	 */
	private int getMaxStringLength(int index) {
		/* Maximum length of the strings an the column. */
		int maxStringLength = 5;
		/* A list of all strings in a column. */
		List<String> column = getColumn(index);
		
		// Calculate the length of the column by looking at its longest string.
		for (String string : column) {
			maxStringLength = Math.max(maxStringLength, string.length());
		}
		
		// Take header and footer into account.
		if (!this.header.isEmpty())
			maxStringLength = Math.max(maxStringLength, this.header.get(index).length());
		if (!this.footer.isEmpty())
			maxStringLength = Math.max(maxStringLength, this.footer.get(index).length());
		
		// Return.
		return maxStringLength;
	}
	
}
