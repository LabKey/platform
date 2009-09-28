Ext.override(Ext.form.Field, {
	markInvalid : function(msg){
		if(!this.rendered || this.preventMark){
			return;
		}
		var markEl = this.markEl || this.el;
		markEl.addClass(this.invalidClass);
		msg = msg || this.invalidText;
		switch(this.msgTarget){
			case 'qtip':
				markEl.dom.qtip = msg;
				markEl.dom.qclass = 'x-form-invalid-tip';
				if(Ext.QuickTips){
					Ext.QuickTips.enable();
				}
				break;
			case 'title':
				markEl.dom.title = msg;
				break;
			case 'under':
				if(!this.errorEl){
					var elp = this.getErrorCt();
					if(!elp){
						markEl.dom.title = msg;
						break;
					}
					this.errorEl = elp.createChild({cls:'x-form-invalid-msg'});
					this.errorEl.setWidth(elp.getWidth(true)-20);
				}
				this.errorEl.update(msg);
				Ext.form.Field.msgFx[this.msgFx].show(this.errorEl, this);
				break;
			case 'side':
				if(!this.errorIcon){
					var elp = this.getErrorCt();
					if(!elp){
						markEl.dom.title = msg;
						break;
					}
					this.errorIcon = elp.createChild({cls:'x-form-invalid-icon'});
				}
				this.alignErrorIcon();
				this.errorIcon.dom.qtip = msg;
				this.errorIcon.dom.qclass = 'x-form-invalid-tip';
				this.errorIcon.show();
				this.on('resize', this.alignErrorIcon, this);
				break;
			default:
				var t = Ext.getDom(this.msgTarget);
				t.innerHTML = msg;
				t.style.display = this.msgDisplay;
				break;
		}
		this.fireEvent('invalid', this, msg);
	},
	clearInvalid : function(){
		if(!this.rendered || this.preventMark){
			return;
		}
		var markEl = this.markEl || this.el;
		markEl.removeClass(this.invalidClass);
		switch(this.msgTarget){
			case 'qtip':
				markEl.dom.qtip = '';
				break;
			case 'title':
				markEl.dom.title = '';
				break;
			case 'under':
				if(this.errorEl){
					Ext.form.Field.msgFx[this.msgFx].hide(this.errorEl, this);
				}else{
					markEl.dom.title = '';
				}
				break;
			case 'side':
				if(this.errorIcon){
					this.errorIcon.dom.qtip = '';
					this.errorIcon.hide();
					this.un('resize', this.alignErrorIcon, this);
				}else{
					markEl.dom.title = '';
				}
				break;
			default:
				var t = Ext.getDom(this.msgTarget);
				t.innerHTML = '';
				t.style.display = 'none';
				break;
		}
		this.fireEvent('valid', this);
	},
	alignErrorIcon : function(){
		this.errorIcon.alignTo(this.markEl || this.el, 'tl-tr', [2, 0]);
	}
});
Ext.override(Ext.form.Checkbox, {
	onRender: function(ct, position){
		Ext.form.Checkbox.superclass.onRender.call(this, ct, position);
		if(this.inputValue !== undefined){
			this.el.dom.value = this.inputValue;
		}
		this.el.removeClass(this.baseCls);
		//this.el.addClass('x-hidden');
		this.innerWrap = this.el.wrap({
			//tabIndex: this.tabIndex,
			cls: this.baseCls+'-wrap-inner'
		});
		this.wrap = this.innerWrap.wrap({cls: this.baseCls+'-wrap'});
		this.imageEl = this.innerWrap.createChild({
			tag: 'img',
			src: Ext.BLANK_IMAGE_URL,
			cls: this.baseCls
		});
		if(this.boxLabel){
			this.labelEl = this.innerWrap.createChild({
				tag: 'label',
				htmlFor: this.el.id,
				cls: 'x-form-cb-label',
				html: this.boxLabel
			});
		}
		//this.imageEl = this.innerWrap.createChild({
			//tag: 'img',
			//src: Ext.BLANK_IMAGE_URL,
			//cls: this.baseCls
		//}, this.el);
		if(this.checked){
			this.setValue(true);
		}else{
			this.checked = this.el.dom.checked;
		}
		this.originalValue = this.checked;
		this.markEl = this.innerWrap;
	},
	afterRender: function(){
		Ext.form.Checkbox.superclass.afterRender.call(this);
		//this.wrap[this.checked ? 'addClass' : 'removeClass'](this.checkedCls);
		this.imageEl[this.checked ? 'addClass' : 'removeClass'](this.checkedCls);
	},
	initCheckEvents: function(){
		//this.innerWrap.removeAllListeners();
		this.innerWrap.addClassOnOver(this.overCls);
		this.innerWrap.addClassOnClick(this.mouseDownCls);
		this.innerWrap.on('click', this.onClick, this);
		//this.innerWrap.on('keyup', this.onKeyUp, this);
		if(this.validationEvent !== false){
			this.el.on(this.validationEvent, this.validate, this, {buffer: this.validationDelay});
		}
	},
	onFocus: function(e) {
		Ext.form.Checkbox.superclass.onFocus.call(this, e);
		//this.el.addClass(this.focusCls);
		this.innerWrap.addClass(this.focusCls);
	},
	onBlur: function(e) {
		Ext.form.Checkbox.superclass.onBlur.call(this, e);
		//this.el.removeClass(this.focusCls);
		this.innerWrap.removeClass(this.focusCls);
	},
	onClick: function(e){
		if (e.getTarget().htmlFor != this.el.dom.id) {
			if (e.getTarget() != this.el.dom) {
				this.el.focus();
			}
			if (!this.disabled && !this.readOnly) {
				this.toggleValue();
			}
		}
		//e.stopEvent();
	},
	onEnable: Ext.form.Checkbox.superclass.onEnable,
	onDisable: Ext.form.Checkbox.superclass.onDisable,
	onKeyUp: undefined,
	setValue: function(v) {
		var checked = this.checked;
		this.checked = (v === true || v === 'true' || v == '1' || String(v).toLowerCase() == 'on');
		if(this.rendered){
			this.el.dom.checked = this.checked;
			this.el.dom.defaultChecked = this.checked;
			//this.wrap[this.checked ? 'addClass' : 'removeClass'](this.checkedCls);
			this.imageEl[this.checked ? 'addClass' : 'removeClass'](this.checkedCls);
		}
		if(checked != this.checked){
			this.fireEvent("check", this, this.checked);
			if(this.handler){
				this.handler.call(this.scope || this, this, this.checked);
			}
		}
	},
	getResizeEl: function() {
		//if(!this.resizeEl){
			//this.resizeEl = Ext.isSafari ? this.wrap : (this.wrap.up('.x-form-element', 5) || this.wrap);
		//}
		//return this.resizeEl;
		return this.wrap;
	},
	markInvalid: Ext.form.Checkbox.superclass.markInvalid,
	clearInvalid: Ext.form.Checkbox.superclass.clearInvalid,
	validationEvent: 'click',
	validateOnBlur: false,
	validateValue: function(value){
		if(this.vtype){
			var vt = Ext.form.VTypes;
			if(!vt[this.vtype](value, this)){
				this.markInvalid(this.vtypeText || vt[this.vtype +'Text']);
				return false;
			}
		}
		if(typeof this.validator == "function"){
			var msg = this.validator(value);
			if(msg !== true){
				this.markInvalid(msg);
				return false;
			}
		}
		return true;
	}
});
Ext.override(Ext.form.Radio, {
	checkedCls: 'x-form-radio-checked',
	markInvalid: Ext.form.Radio.superclass.markInvalid,
	clearInvalid: Ext.form.Radio.superclass.clearInvalid
});