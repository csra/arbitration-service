/* 
 * Copyright (C) 2016 Bielefeld University, Patrick Holthaus
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.citec.csra.task;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.converter.ConversionException;
import rsb.converter.Converter;
import rsb.converter.ConverterRepository;
import rsb.converter.DefaultConverterRepository;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SerializationService<T> {

	private final Converter<ByteBuffer> converter;
	private final String schema;
	private final Class<T> cls;

	public SerializationService(Class<T> type) throws InitializeException {

		ConverterRepository<ByteBuffer> def = DefaultConverterRepository.getDefaultConverterRepository();
		this.converter = def.getConvertersForSerialization().getConverter(type.getName());
		this.schema = converter.getSignature().getSchema();
		this.cls = type;
	}
	
	public T deserialize(ByteString bytes) {
		try {
			return (T) this.converter.deserialize(this.schema, bytes.asReadOnlyByteBuffer()).getData();
		} catch (ConversionException ex) {
			Logger.getLogger(SerializationService.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}

	public ByteString serialize(T data) {
		try {
			return ByteString.copyFrom(this.converter.serialize(this.cls, data).getSerialization());
		} catch (ConversionException ex) {
			Logger.getLogger(SerializationService.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}
	
	public ByteString getSchema() {
		return ByteString.copyFromUtf8(this.schema);
	}
	
}
