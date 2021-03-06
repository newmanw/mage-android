package mil.nga.giat.mage.form.field

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import mil.nga.giat.mage.databinding.ViewFormCheckboxBinding
import mil.nga.giat.mage.databinding.ViewFormEditCheckboxBinding
import mil.nga.giat.mage.form.FormField

class ViewCheckbox @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<Boolean>(context, attrs, defStyle, defStyleRes) {

    private val binding: ViewFormCheckboxBinding = ViewFormCheckboxBinding.inflate(LayoutInflater.from(context), this, true)

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<Boolean>) {
        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField
    }
}

class EditCheckbox @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : Field<Boolean>(context, attrs, defStyle, defStyleRes)  {
    private val binding: ViewFormEditCheckboxBinding = ViewFormEditCheckboxBinding.inflate(LayoutInflater.from(context), this, true)

    override fun bind(lifecycleOwner: LifecycleOwner, formField: FormField<Boolean>) {
        binding.lifecycleOwner = lifecycleOwner
        binding.field = formField
    }
}
