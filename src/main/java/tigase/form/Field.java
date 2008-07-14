/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.form;

import java.util.LinkedList;
import java.util.List;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-27 10:56:06
 * </p>
 * 
 * @author bmalkow
 * @version $Rev:43 $
 */
public class Field {
	public static enum FieldType {
		bool("boolean"), fixed("fixed"), hidden("hidden"), list_multi("list-multi"), list_single("list-single"), text_multi(
				"text-multi"), text_private("text-private"), text_single("text-single"), jid_single("jid-single");
		public static FieldType getFieldTypeByName(String name) {
			if ("boolean".equals(name)) {
				return bool;
			} else {
				return FieldType.valueOf(name.replace("-", "_"));
			}
		}

		private String desc;

		private FieldType(String desc) {
			this.desc = desc;
		}

		@Override
		public String toString() {
			return desc;
		}
	}

	public static Field fieldBoolean(String var, Boolean value, String label) {
		Field field = new Field(FieldType.bool);
		field.label = label;
		field.var = var;

		if (value != null && value) {
			field.values = new String[] { "1" };
		} else if (value != null && !value) {
			field.values = new String[] { "0" };
		}
		return field;
	}

	public static Field fieldFixed(String value) {
		Field field = new Field(FieldType.fixed);
		field.values = new String[] { value };
		return field;
	}

	public static Field fieldHidden(String var, String value) {
		Field field = new Field(FieldType.hidden, var);
		field.values = new String[] { value };
		return field;
	}

	public static Field fieldJidSingle(String var, String value, String label) {
		Field field = new Field(FieldType.jid_single, var);
		field.label = label;
		field.values = new String[] { value };
		return field;
	}

	public static Field fieldListMulti(String var, String[] values, String label, String[] optionsLabel, String[] optionsValue) {
		if (optionsLabel != null && optionsLabel.length != optionsValue.length) {
			throw new RuntimeException("Invalid optionsLabel and optinsValue length");
		}
		Field field = new Field(FieldType.list_multi, var);
		field.label = label;
		field.values = values;
		field.optionLabels = optionsLabel;
		field.optionValues = optionsValue;
		return field;
	}

	public static Field fieldListSingle(String var, String value, String label, String[] optionsLabel, String[] optionsValue) {
		if (optionsLabel != null && optionsLabel.length != optionsValue.length) {
			throw new RuntimeException("Invalid optionsLabel and optinsValue length");
		}
		Field field = new Field(FieldType.list_single, var);
		field.label = label;
		field.values = new String[] { value };
		field.optionLabels = optionsLabel;
		field.optionValues = optionsValue;
		return field;
	}

	public static Field fieldTextMulti(String var, String value, String label) {
		Field field = new Field(FieldType.text_multi, var);
		field.label = label;
		field.values = new String[] { value };
		return field;
	}

	public static Field fieldTextMulti(String var, String[] values, String label) {
		Field field = new Field(FieldType.text_multi, var);
		field.label = label;
		field.values = values;
		return field;
	}

	public static Field fieldTextPrivate(String var, String value, String label) {
		Field field = new Field(FieldType.text_private, var);
		field.label = label;
		field.values = new String[] { value };
		return field;
	}

	public static Field fieldTextSingle(String var, String value, String label) {
		Field field = new Field(FieldType.text_single, var);
		field.label = label;
		field.values = new String[] { value };
		return field;
	}

	private String description;

	private String label;

	private String[] optionLabels;

	private String[] optionValues;

	private boolean required;

	private FieldType type;

	private String[] values;

	private String var;

	public Field(Element fieldElement) {
		this.var = fieldElement.getAttribute("var");
		this.type = FieldType.getFieldTypeByName(fieldElement.getAttribute("type"));
		this.label = fieldElement.getAttribute("label");
		Element d = fieldElement.getChild("desc");
		if (d != null) {
			this.description = d.getCData();
		}
		this.required = fieldElement.getChild("required") != null;

		List<String> valueList = new LinkedList<String>();
		List<String> optionsLabelList = new LinkedList<String>();
		List<String> optionsValueList = new LinkedList<String>();

		for (Element element : fieldElement.getChildren()) {
			if ("value".equals(element.getName())) {
				valueList.add(element.getCData());
			} else if ("value".equals(element.getName())) {
				optionsLabelList.add(element.getAttribute("label"));
				Element v = element.getChild("value");
				optionsValueList.add(v.getCData());
			}
		}
		this.values = valueList.toArray(new String[] {});
		this.optionLabels = optionsLabelList.toArray(new String[] {});
		this.optionValues = optionsValueList.toArray(new String[] {});
	}

	private Field(FieldType type) {
		this.type = type;
	}

	private Field(FieldType type, String var) {
		this.type = type;
		this.var = var;
	}

	/**
	 * @return Returns the description.
	 */
	public String getDescription() {
		return description;
	}

	public Element getElement() {
		Element field = new Element("field");
		if (this.var != null) {
			field.setAttribute("var", var);
		}
		if (this.type != null) {
			field.setAttribute("type", this.type.toString());
		}
		if (this.label != null) {
			field.setAttribute("label", this.label);
		}

		if (this.description != null) {
			field.addChild(new Element("desc", this.description));
		}

		if (this.required) {
			field.addChild(new Element("required"));
		}

		if (this.values != null) {
			for (String value : this.values) {
				field.addChild(new Element("value", value));
			}
		}

		if (this.optionValues != null) {
			for (int i = 0; i < this.optionValues.length; i++) {
				Element option = new Element("option");
				if (this.optionLabels != null && i < this.optionLabels.length) {
					option.setAttribute("label", this.optionLabels[i]);
				}
				Element vo = new Element("value", this.optionValues[i]);
				option.addChild(vo);
				field.addChild(option);
			}
		}

		return field;
	}

	/**
	 * @return Returns the label.
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return Returns the optionLabels.
	 */
	public String[] getOptionLabels() {
		return optionLabels;
	}

	/**
	 * @return Returns the optionValues.
	 */
	public String[] getOptionValues() {
		return optionValues;
	}

	/**
	 * @return Returns the type.
	 */
	public FieldType getType() {
		return type;
	}

	public String getValue() {
		if (this.values != null && this.values.length > 0) {
			return this.values[0];
		} else
			return null;
	}

	/**
	 * @return Returns the values.
	 */
	public String[] getValues() {
		return values;
	}

	/**
	 * @return Returns the var.
	 */
	public String getVar() {
		return var;
	}

	/**
	 * @return Returns the required.
	 */
	public boolean isRequired() {
		return required;
	}

	/**
	 * @param description
	 *            The description to set.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @param label
	 *            The label to set.
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 * @param optionLabels
	 *            The optionLabels to set.
	 */
	public void setOptionLabels(String[] optionLabels) {
		this.optionLabels = optionLabels;
	}

	/**
	 * @param optionValues
	 *            The optionValues to set.
	 */
	public void setOptionValues(String[] optionValues) {
		this.optionValues = optionValues;
	}

	/**
	 * @param required
	 *            The required to set.
	 */
	public void setRequired(boolean required) {
		this.required = required;
	}

	/**
	 * @param type
	 *            The type to set.
	 */
	public void setType(FieldType type) {
		this.type = type;
	}

	/**
	 * @param values
	 *            The values to set.
	 */
	public void setValues(String[] values) {
		this.values = values;
	}

	/**
	 * @param var
	 *            The var to set.
	 */
	public void setVar(String var) {
		this.var = var;
	}

}