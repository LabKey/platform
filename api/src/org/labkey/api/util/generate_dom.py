empty_tags = [
	"area",
	"base",
	"br",
	"col",
	"embed",
	"hr",
	"img",
	"input",
	"keygen",
	"link",
	"meta",
	"param",
	"source",
	"track",
	"wbr" ]

tags = [
	"a",
	"abbr",
	"address",
	"area",
	"article",
	"aside",
	"audio",
	"b",
	"base",
	"bdi",
	"bdo",
	"big",
	"blockquote",
	"body",
	"br",
	"button",
	"canvas",
	"caption",
	"cite",
	"code",
	"col",
	"colgroup",
	"data",
	"datalist",
	"dd",
	"del",
	"details",
	"dfn",
	"dialog",
	"div",
	"dl",
	"dt",
	"em",
	"embed",
	"fieldset",
	"figcaption",
	"figure",
	"footer",
	"form",
	"h1",
	"h2",
	"h3",
	"h4",
	"h5",
	"h6",
	"head",
	"header",
	"hgroup",
	"hr",
	"html",
	"i",
	"iframe",
	"img",
	"input",
	"ins",
	"kbd",
	"keygen",
	"label",
	"legend",
	"li",
	"link",
	"main",
	"map",
	"mark",
	"menu",
	"menuitem",
	"meta",
	"meter",
	"nav",
	"noindex",
	"noscript",
	"object",
	"ol",
	"optgroup",
	"option",
	"output",
	"p",
	"param",
	"picture",
	"pre",
	"progress",
	"q",
	"rp",
	"rt",
	"ruby",
	"s",
	"samp",
	"script",
	"section",
	"select",
	"small",
	"source",
	"span",
	"strong",
	"style",
	"sub",
	"summary",
	"sup",
	"table",
	"tbody",
	"td",
	"textarea",
	"tfoot",
	"th",
	"thead",
	"time",
	"title",
	"tr",
	"track",
	"u",
	"ul",
	"var",
	"video",
	"wbr",
	"webview"]


for tag in empty_tags:
	# print("""	public static Renderable<Appendable> %s(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames)
	# {
	# 	return (html) -> Element.option.render(html, attrs, classNames);
	# }""" % (tag.upper()))
	print("""	public static Renderable<Appendable> %s(Iterable<Map.Entry<Object, Object>> attrs)
	{
		return (html) -> Element.%s.render(html, attrs);
	}""" % (tag.upper(),tag.lower()))
	# print("""	public static Renderable<Appendable> %s(ClassNames classNames)
	# {
	# 	return (html) -> Element.option.render(html, NOAT, classNames);
	# }""" % (tag.upper()))
	print("""	public static Renderable<Appendable> %s()
	{
		return (html) -> Element.%s.render(html, NOAT);
	}""" % (tag.upper(),tag.lower()))
for tag in tags:
	# print("""	public static Renderable<Appendable> %s(Iterable<Map.Entry<Object, Object>> attrs, ClassNames classNames, Object... body)
	# {
	# 	return (html) -> Element.option.render(html, attrs, classNames, body);
	# }""" % (tag.upper()))
	print("""	public static Renderable<Appendable> %s(Iterable<Map.Entry<Object, Object>> attrs, Object... body)
	{
		return (html) -> Element.%s.render(html, attrs, body);
	}""" % (tag.upper(),tag.lower()))
	# print("""	public static Renderable<Appendable> %s(ClassNames classNames, Object... body)
	# {
	# 	return (html) -> Element.option.render(html, NOAT, classNames, body);
	# }""" % (tag.upper()))
	print("""	public static Renderable<Appendable> %s(Object... body)
	{
		return (html) -> Element.%s.render(html, NOAT, body);
	}""" % (tag.upper(),tag.lower()))