/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
