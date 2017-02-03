package com.chemfizlab.scioapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.consumerphysics.android.sdk.model.ScioModel;
import com.consumerphysics.android.sdk.model.attribute.ScioAttribute;
import com.consumerphysics.android.sdk.model.attribute.ScioDatetimeAttribute;
import com.consumerphysics.android.sdk.model.attribute.ScioNumericAttribute;
import com.consumerphysics.android.sdk.model.attribute.ScioStringAttribute;

import java.util.List;

import com.chemfizlab.scioapp.R;

/**
 * Created by nadavg on 16/02/2016.
 */
public class ScioModelAdapter extends ArrayAdapter<ScioModel> {
    public ScioModelAdapter(final Context context, final List<ScioModel> models) {
        super(context, 0, models);
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.results_item, parent, false);
        }

        final TextView attributeName = (TextView) convertView.findViewById(R.id.attribute_name);
        final TextView attributeValue = (TextView) convertView.findViewById(R.id.attribute_value);
        attributeValue.setText("");

        final ScioModel model = getItem(position);

        attributeName.setText(model.getName());

        if (model.getAttributes() != null && !model.getAttributes().isEmpty()) {
            for (ScioAttribute attribute : model.getAttributes()) {
                String value;
                String unit = null;

                /**
                 * Classification model will return a STRING value.
                 * Estimation will return the NUMERIC value.
                 */
                switch (attribute.getAttributeType()) {
                    case STRING:
                        value = ((ScioStringAttribute) (attribute)).getValue();
                        break;
                    case NUMERIC:
                        value = String.valueOf(((ScioNumericAttribute) (attribute)).getValue());
                        unit = attribute.getUnits();
                        break;
                    case DATE_TIME:
                        value = ((ScioDatetimeAttribute) (attribute)).getValue().toString();
                        break;
                    default:
                        continue;
                }

                if (!isNull(attribute.getLabel())) {
                    value = attribute.getLabel() + " " + value;
                }

                if (model.getType().equals(ScioModel.Type.ESTIMATION)) {
                    if (isNull(unit)) {
                        attributeValue.setText(value);
                    }
                    else {
                        value = value + unit;
                    }
                }
                else {
                    value = value + " (" + String.format("%.2f", attribute.getConfidence()) + ")";
                }

                attributeValue.setText(attributeValue.getText().toString() + value + ", ");
            }
        }
        else {
            attributeValue.setText("N/A");
        }

        return convertView;
    }

    private boolean isNull(final String value) {
        return value == null || value.equals("null");
    }
}
