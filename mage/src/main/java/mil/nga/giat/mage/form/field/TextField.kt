package mil.nga.giat.mage.form.field

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.view_form_edit_text.view.*
import mil.nga.giat.mage.databinding.ViewFormEditTextBinding
import mil.nga.giat.mage.databinding.ViewFormTextBinding
import mil.nga.giat.mage.form.FormField

class ViewText @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<String>(context, attrs, defStyle, defStyleRes) {

    private val binding: ViewFormTextBinding = ViewFormTextBinding.inflate(LayoutInflater.from(context), this, true)

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<String>) {
        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField
    }
}

class EditText @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<String>(context, attrs, defStyle, defStyleRes) {

    private var required = false
    private val binding: ViewFormEditTextBinding = ViewFormEditTextBinding.inflate(LayoutInflater.from(context), this, true)

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<String>) {
        required = formField.required

        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField
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
