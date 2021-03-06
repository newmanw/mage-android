package mil.nga.giat.mage.form.field

import android.content.Context
import android.util.AttributeSet
import android.util.Patterns
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.view_form_edit_email.view.*
import mil.nga.giat.mage.databinding.ViewFormEditEmailBinding
import mil.nga.giat.mage.form.FormField
import mil.nga.giat.mage.form.TextFormField

class EmailField @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<String>(context, attrs, defStyle, defStyleRes) {

    private val binding: ViewFormEditEmailBinding = ViewFormEditEmailBinding.inflate(LayoutInflater.from(context), this, true)
    private var required = false

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<String>) {
        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField as TextFormField

        required = formField.required
    }

    override fun validate(enforceRequired: Boolean): Boolean {
        if (enforceRequired && required && editText.text.isNullOrBlank()) {
            textInputLayout.error  = "Required, cannot be blank"
            return false
        } else {
            textInputLayout.isErrorEnabled = false
        }

        val value = editText.text.toString()
        if (value.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            textInputLayout.error = "Invalid email address"
            return false
        }

        return true
    }
}
