package mil.nga.giat.mage.form

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.fragement_form.*
import mil.nga.giat.mage.R
import mil.nga.giat.mage.form.field.*
import mil.nga.giat.mage.form.field.dialog.DateFieldDialog
import mil.nga.giat.mage.form.field.dialog.GeometryFieldDialog
import mil.nga.giat.mage.form.field.dialog.SelectFieldDialog
import mil.nga.giat.mage.observation.ObservationLocation
import java.util.Date;
import kotlin.collections.ArrayList

class FormFragment : Fragment() {

    private lateinit var model: FormViewModel

    val editFields = ArrayList<Field<out Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProvider(this).get(FormViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
       return inflater.inflate(R.layout.fragement_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        model.form.observe(viewLifecycleOwner, Observer { form ->
            form?.let { buildForm(it) }
        })
    }

    private fun buildForm(form: Form) {
        forms.removeAllViews()

        val fields = form.fields
                .filterNot { it.archived }
                .sortedBy { it.id }

        for (field in fields) {
            createField(requireContext(), field)?.let {
                forms.addView(it)
                editFields.add(it)
            }
        }
    }

    private fun createField(context: Context, field: FormField<in Any>): Field<out Any>? {
        return when (model.formMode) {
            FormMode.VIEW -> {
                return if (field.hasValue()) {
                    createViewField(context, field)
                } else null
            }
            FormMode.EDIT -> {
                createEditField(context, field)
            }
        }
    }

    private fun createViewField(context: Context, field: FormField<in Any>): Field<out Any>? {
        return when(field.type) {
            FieldType.TEXTFIELD -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.TEXTAREA -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.EMAIL -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.PASSWORD -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.NUMBERFIELD -> {
                val view = ViewNumber(context)
                view.bind(viewLifecycleOwner, field as FormField<Number>)
                view
            }
            FieldType.DATE -> {
                val view = ViewDate(context)
                view.bind(viewLifecycleOwner, field as FormField<Date>)
                view
            }
            FieldType.RADIO -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.CHECKBOX -> {
                val view = ViewCheckbox(context)
                view.bind(viewLifecycleOwner, field as FormField<Boolean>)
                view
            }
            FieldType.DROPDOWN -> {
                val view = ViewText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.MULTISELECTDROPDOWN -> {
                val view = ViewMultiselect(context)
                view.bind(viewLifecycleOwner, field as ChoiceFormField<List<String>>)
                view
            }
            FieldType.GEOMETRY -> {
                val view = ViewGeometry(context)
                view.bind(viewLifecycleOwner, field as FormField<ObservationLocation>)
                view
            }
        }
    }

    private fun createEditField(context: Context, field: FormField<in Any>): Field<out Any> {
        return when(field.type) {
            FieldType.TEXTFIELD -> {
                val view = EditText(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.TEXTAREA -> {
                val view = EditTextarea(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.EMAIL -> {
                val view = EmailField(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.PASSWORD -> {
                val view = PasswordField(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.NUMBERFIELD -> {
                val view = EditNumber(context)
                view.bind(viewLifecycleOwner, field as FormField<Number>)
                view
            }
            FieldType.DATE -> {
                val view = EditDate(context)
                view.bind(viewLifecycleOwner, field as FormField<Date>)
                view.setOnEditDateClickListener { onDateFieldClick(field) }
                view
            }
            FieldType.RADIO -> {
                val view = RadioField(context)
                view.bind(viewLifecycleOwner, field as FormField<String>)
                view
            }
            FieldType.CHECKBOX -> {
                val view = EditCheckbox(context)
                view.bind(viewLifecycleOwner, field as FormField<Boolean>)
                view
            }
            FieldType.DROPDOWN -> {
                val view = EditSelect(context)
                view.bind(viewLifecycleOwner, field as ChoiceFormField<String>)
                view.setOnEditSelectClickListener {
                    onSelectFieldClick(field)
                }

                view
            }
            FieldType.MULTISELECTDROPDOWN -> {
                val view = EditMultiSelect(context)
                view.bind(viewLifecycleOwner, field as ChoiceFormField<List<String>>)
                view.setOnEditSelectClickListener {
                    onSelectFieldClick(field)
                }

                view
            }
            FieldType.GEOMETRY -> {
                val view = EditGeometry(context)
                view.bind(viewLifecycleOwner, field as FormField<ObservationLocation>)
                view.setOnEditGeometryClickListener {
                    onGeometryFieldClick(field)
                }

                view
            }
        }
    }

    private fun onDateFieldClick(field: FormField<*>) {
        val dialog = DateFieldDialog.newInstance(field.name)
        activity?.supportFragmentManager?.let {
            dialog.show(it, "DIALOG_DATE_FIELD")
        }
    }

    private fun onSelectFieldClick(field: FormField<*>) {
        val dialog = SelectFieldDialog.newInstance(field.name)
        activity?.supportFragmentManager?.let {
            dialog.show(it, "DIALOG_SELECT_FIELD")
        }
    }

    private fun onGeometryFieldClick(field: FormField<*>) {
        val dialog = GeometryFieldDialog.newInstance(field.name)
        activity?.supportFragmentManager?.let {
            dialog.show(it, "DIALOG_GEOMETRY_FIELD")
        }
    }
}