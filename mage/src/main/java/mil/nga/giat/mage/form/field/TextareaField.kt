package mil.nga.giat.mage.form.field

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.view_form_edit_textarea.view.*
import mil.nga.giat.mage.databinding.ViewFormEditTextareaBinding
import mil.nga.giat.mage.form.FormField

class EditTextarea @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<String>(context, attrs, defStyle, defStyleRes) {

    private val binding: ViewFormEditTextareaBinding = ViewFormEditTextareaBinding.inflate(LayoutInflater.from(context), this, true)
    private var required = false

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<String>) {
        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField

        required = formField.required
    }

    override fun validate(enforceRequired: Boolean): Boolean {
        if (enforceRequired && required && editText.text.isNullOrBlank()) {
            textInputLayout.error  = "Required, cannot be blank"
            return false
        } else {
            textInputLayout.isErrorEnabled = false
        }

        return true
    }
}
