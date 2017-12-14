package org.pentaho.reporting.engine.classic.core.layout.richtext;

import org.pentaho.reporting.engine.classic.core.AttributeNames;
import org.pentaho.reporting.engine.classic.core.Element;
import org.pentaho.reporting.engine.classic.core.modules.parser.base.ReportParserUtil;
import org.pentaho.reporting.engine.classic.core.style.*;
import org.pentaho.reporting.libraries.base.util.StringUtils;
import org.pentaho.reporting.libraries.xmlns.parser.ParseException;

import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;


public class HtmlStylesRichTechConverter {



    /**
     * Type of list (items), specified by list-style CSS property (and or HTML4's type attribute).
     */
    public static enum ListStyle {
        NONE, DISC, CIRCLE, SQUARE, //
        ARABIC, LOWER_ROMAN, CAPS_ROMAN, LOWER_ALPHA, CAPS_ALPHA;

        /**
         * Converts given item to String in particular format.
         *
         * @param item number 1, 2, 3, ...
         * @return string representation
         */
        public String convert(int item) {
            switch (this) {
                case NONE:
                    return "";

                case DISC:
                    return "() ";

                case CIRCLE:
                    return "o ";

                case SQUARE:
                    return "[] ";

                case ARABIC:
                    return Integer.toString(item) + ". ";

                case LOWER_ROMAN:
                    return toRoman(item).toLowerCase() + ". ";

                case CAPS_ROMAN:
                    return toRoman(item) + ". ";

                case LOWER_ALPHA:
                    return toAlpha(item).toLowerCase() + ". ";

                case CAPS_ALPHA:
                    return toAlpha(item) + ". ";
            }

            return null;
        }

        protected static String toAlpha(int number) {
            String result = ""; // since we assume no more than "ZZ" items is here String more effective than StringBuilder

            if (number <= 0) {
                return result;
            }
            do {
                number--;
                result = ((char) (((number % ('Z' - 'A' + 1)) + 'A'))) + result;
                number = number / ('Z' - 'A' + 1);
            } while (number > 0);

            return result;
        }

        protected static String toRoman(int number) {
           String result = "";
            final int[] decimal = new int[]{1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
            final String[]  roman = new String[]{"M", "CM","D","CD","C", "XC", "L", "XL", "X","IX","V","IV","I"};
            for (int i = 0; i < decimal.length; i++) {
                while (number % decimal[i] < number) {
                    result += roman[i];
                    number -= decimal[i];
                }
            }
            return result;
        }

        public static ListStyle parse(String cssText) {
            if ("none.".equals(cssText)) {
                return ListStyle.NONE;
            }
            if ("circle".equals(cssText)) {
                return ListStyle.CIRCLE;
            }
            if ("disc".equals(cssText)) {
                return ListStyle.DISC;
            }
            if ("square".equals(cssText)) {
                return ListStyle.SQUARE;
            }
            if ("decimal".equals(cssText) || "arabic".equals(cssText)) { //just for case
                return ListStyle.ARABIC;
            }
            if ("lower-alpha".equals(cssText)) {
                return ListStyle.LOWER_ALPHA;
            }
            if ("upper-alpha".equals(cssText)) {
                return ListStyle.CAPS_ALPHA;
            }
            if ("lower-roman".equals(cssText)) {
                return ListStyle.LOWER_ROMAN;
            }
            if ("upper-roman".equals(cssText)) {
                return ListStyle.CAPS_ROMAN;
            }

            return null;
        }
    }


    public AttributeSet computeStyle( final javax.swing.text.Element elem, final StyleSheet styles ) {
        final AttributeSet a = elem.getAttributes();
        final AttributeSet htmlAttr = styles.translateHTMLToCSS( a );
        final ArrayList<AttributeSet> muxList = new ArrayList<AttributeSet>();

        if ( htmlAttr.getAttributeCount() != 0 ) {
            muxList.add( htmlAttr );
        }

        if ( elem.isLeaf() ) {
            // The swing-parser has a very weird way of storing attributes for the HTML elements. The
            // tag-name is used as key for the attribute set, so you have to know the element type before
            // you can do anything sensible with it. Or as we do here, you have to search for the HTML.Tag
            // object. Arghh.
            final Enumeration keys = a.getAttributeNames();
            while ( keys.hasMoreElements() ) {
                final Object key = keys.nextElement();
                if ( !( key instanceof HTML.Tag ) ) {
                    continue;
                }

                if ( key == HTML.Tag.A ) {
                    final Object o = a.getAttribute( key );
                    if ( o instanceof AttributeSet ) {
                        final AttributeSet attr = (AttributeSet) o;
                        if ( attr.getAttribute( HTML.Attribute.HREF ) != null ) {
                            SimpleAttributeSet hrefAttributeSet = new SimpleAttributeSet();
                            hrefAttributeSet.addAttribute( HTML.Attribute.HREF, attr.getAttribute( HTML.Attribute.HREF ) );
                            muxList.add( hrefAttributeSet );
                        }
                    }
                }

                final AttributeSet cssRule = styles.getRule( (HTML.Tag) key, elem );
                if ( cssRule != null ) {
                    muxList.add( cssRule );
                }
            }
        } else {
            final HTML.Tag t = (HTML.Tag) a.getAttribute( StyleConstants.NameAttribute );
            final AttributeSet cssRule = styles.getRule( t, elem );
            if ( cssRule != null ) {
                muxList.add( cssRule );
            }
        }

        return listToSet(muxList);
    }

    private AttributeSet listToSet(ArrayList<AttributeSet> muxList) {
        final MutableAttributeSet retval = new SimpleAttributeSet();

        for ( int i = muxList.size() - 1; i >= 0; i-- ) {
            final AttributeSet o = muxList.get( i );
            retval.addAttributes( o );
        }

        return retval;
    }


    public void configureStyle( final javax.swing.text.Element textElement, final Element result ) {
        final HTMLDocument htmlDocument = (HTMLDocument) textElement.getDocument();
        final StyleSheet sheet = htmlDocument.getStyleSheet();
        final AttributeSet attr = computeStyle( textElement, sheet );

        parseBgAndFgColors(result, sheet, attr);
        parseBorder( result, sheet, attr );
        parseBoxStyle( result, attr );
        parseFont(result, sheet, attr);
        parseSpacing(result, attr);
        parseAlignments(result, attr);
        parseTextDecoration(result, attr);
        parseWhitespace(result, attr);
        parseCssListStyles(result, attr);
        parseCssTheRestStyles(result, attr);

        parseAditionalStyleProperties(result, attr);
    }

    private void parseAditionalStyleProperties(Element result, AttributeSet attr) {
        final Object hrefAttribute = attr.getAttribute( HTML.Attribute.HREF );
        if ( hrefAttribute != null ) {
            result.getStyle().setStyleProperty( ElementStyleKeys.HREF_TARGET, String.valueOf( hrefAttribute ) );
        }
        final Object titleAttribute = attr.getAttribute( HTML.Attribute.TITLE );
        if ( titleAttribute != null ) {
            result.setAttribute( AttributeNames.Html.NAMESPACE, AttributeNames.Html.TITLE, String.valueOf( titleAttribute ) );
        }
    }

    private void parseWhitespace(Element result, AttributeSet attr) {
        final Object whitespaceText = attr.getAttribute( CSS.Attribute.WHITE_SPACE );
        if ( whitespaceText != null ) {
            final String value = String.valueOf( whitespaceText );
            if ( "pre".equals( value ) ) {
                result.getStyle().setStyleProperty( TextStyleKeys.WHITE_SPACE_COLLAPSE, WhitespaceCollapse.PRESERVE );
                result.getStyle().setStyleProperty( TextStyleKeys.TEXT_WRAP, TextWrap.NONE );
            } else if ( "nowrap".equals( value ) ) {
                result.getStyle().setStyleProperty( TextStyleKeys.WHITE_SPACE_COLLAPSE, WhitespaceCollapse.PRESERVE_BREAKS );
                result.getStyle().setStyleProperty( TextStyleKeys.TEXT_WRAP, TextWrap.NONE );
            } else {
                result.getStyle().setStyleProperty( TextStyleKeys.WHITE_SPACE_COLLAPSE, WhitespaceCollapse.COLLAPSE );
                result.getStyle().setStyleProperty( TextStyleKeys.TEXT_WRAP, TextWrap.WRAP );
            }
        } else {
            result.getStyle().setStyleProperty( TextStyleKeys.WHITE_SPACE_COLLAPSE, WhitespaceCollapse.COLLAPSE );
            result.getStyle().setStyleProperty( TextStyleKeys.TEXT_WRAP, TextWrap.WRAP );
        }
    }

    private void parseTextDecoration(Element result, AttributeSet attr) {
        final Object textDecoration = attr.getAttribute( CSS.Attribute.TEXT_DECORATION );
        if ( textDecoration != null ) {
            final String[] strings = StringUtils.split( String.valueOf( textDecoration ) );
            result.getStyle().setStyleProperty( TextStyleKeys.STRIKETHROUGH, Boolean.FALSE );
            result.getStyle().setStyleProperty( TextStyleKeys.UNDERLINED, Boolean.FALSE );

            for ( int i = 0; i < strings.length; i++ ) {
                final String value = strings[i];
                if ( "line-through".equals( value ) ) {
                    result.getStyle().setStyleProperty( TextStyleKeys.STRIKETHROUGH, Boolean.TRUE );
                }
                if ( "underline".equals( value ) ) {
                    result.getStyle().setStyleProperty( TextStyleKeys.UNDERLINED, Boolean.TRUE );
                }
            }
        }
    }

    private void parseAlignments(Element result, AttributeSet attr) {
        final Object alignAttribute = attr.getAttribute( HTML.Attribute.ALIGN );
        if ( alignAttribute != null ) {
            try {
                result.getStyle().setStyleProperty( ElementStyleKeys.ALIGNMENT,
                        ReportParserUtil.parseHorizontalElementAlignment( String.valueOf( alignAttribute ), null ) );
            } catch ( ParseException e ) {
                // ignore ..
            }
        }

        final Object textAlign = attr.getAttribute( CSS.Attribute.TEXT_ALIGN );
        if ( textAlign != null ) {
            try {
                result.getStyle().setStyleProperty( ElementStyleKeys.ALIGNMENT,
                        ReportParserUtil.parseHorizontalElementAlignment( String.valueOf( textAlign ), null ) );
            } catch ( ParseException e ) {
                // ignore ..
            }
        }

        final Object valign = attr.getAttribute( CSS.Attribute.VERTICAL_ALIGN );
        if ( valign != null ) {
            final VerticalTextAlign valignValue = VerticalTextAlign.valueOf( String.valueOf( valign ) );
            result.getStyle().setStyleProperty( TextStyleKeys.VERTICAL_TEXT_ALIGNMENT, valignValue );
            try {
                result.getStyle().setStyleProperty( ElementStyleKeys.VALIGNMENT,
                        ReportParserUtil.parseVerticalElementAlignment( String.valueOf( valign ), null ) );
            } catch ( ParseException e ) {
                // ignore ..
            }
        }


    }

    private void parseSpacing(Element result, AttributeSet attr) {
        final Object letterSpacing = attr.getAttribute( CSS.Attribute.LETTER_SPACING );
        if ( letterSpacing != null ) {
            result.getStyle().setStyleProperty( TextStyleKeys.X_OPTIMUM_LETTER_SPACING,
                    parseLength( String.valueOf( letterSpacing ) ) );
        }

        final Object wordSpacing = attr.getAttribute( CSS.Attribute.WORD_SPACING );
        if ( wordSpacing != null ) {
            result.getStyle().setStyleProperty( TextStyleKeys.WORD_SPACING, parseLength( String.valueOf( wordSpacing ) ) );
        }

        final Object lineHeight = attr.getAttribute( CSS.Attribute.LINE_HEIGHT );
        if ( lineHeight != null ) {
            result.getStyle().setStyleProperty( TextStyleKeys.LINEHEIGHT, parseLength( String.valueOf( lineHeight ) ) );
        }

        final Object textIndentStyle = attr.getAttribute( CSS.Attribute.TEXT_INDENT );
        if ( textIndentStyle != null ) {
            result.getStyle().setStyleProperty( TextStyleKeys.FIRST_LINE_INDENT,
                    parseLength( String.valueOf( textIndentStyle ) ) );
        }
    }

    private void parseFont(Element result, StyleSheet sheet, AttributeSet attr) {
        final Font font = sheet.getFont( attr );
        if ( font != null ) {
            result.getStyle().setStyleProperty( TextStyleKeys.FONT, font.getFamily() );
            result.getStyle().setStyleProperty( TextStyleKeys.FONTSIZE, font.getSize() );
            result.getStyle().setBooleanStyleProperty( TextStyleKeys.ITALIC, font.isItalic() );
            result.getStyle().setBooleanStyleProperty( TextStyleKeys.BOLD, font.isBold() );
        }
    }


    private void parseBoxStyle( final Element result, final AttributeSet attr ) {
        parsePadding(result, attr);
        parseMargin(result, attr);
        parseSize(result, attr);
    }


    private void parsePadding(Element result, AttributeSet attr) {
        final Object paddingText = attr.getAttribute( CSS.Attribute.PADDING );
        if ( paddingText != null ) {
            final Float padding = parseLength( String.valueOf( paddingText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_TOP, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_LEFT, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_BOTTOM, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_RIGHT, padding );
        }

        final Object paddingTop = attr.getAttribute( CSS.Attribute.PADDING_TOP );
        if ( paddingTop != null ) {
            final Float padding = parseLength( String.valueOf( paddingTop ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_TOP, padding );
        }
        final Object paddingLeft = attr.getAttribute( CSS.Attribute.PADDING_LEFT );
        if ( paddingLeft != null ) {
            final Float padding = parseLength( String.valueOf( paddingLeft ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_LEFT, padding );
        }
        final Object paddingBottom = attr.getAttribute( CSS.Attribute.PADDING_BOTTOM );
        if ( paddingBottom != null ) {
            final Float padding = parseLength( String.valueOf( paddingBottom ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_BOTTOM, padding );
        }
        final Object paddingRight = attr.getAttribute( CSS.Attribute.PADDING_RIGHT );
        if ( paddingRight != null ) {
            final Float padding = parseLength( String.valueOf( paddingRight ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_RIGHT, padding );
        }
    }
    private void parseMargin(Element result, AttributeSet attr) {
        // margin in our case means just an other word for padding, no difference

        final Object paddingText = attr.getAttribute( CSS.Attribute.MARGIN );
        if ( paddingText != null ) {
            final Float padding = parseLength( String.valueOf( paddingText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_TOP, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_LEFT, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_BOTTOM, padding );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_RIGHT, padding );
        }

        final Object paddingTop = attr.getAttribute( CSS.Attribute.MARGIN_TOP );
        if ( paddingTop != null ) {
            final Float padding = parseLength( String.valueOf( paddingTop ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_TOP, padding );
        }
        final Object paddingLeft = attr.getAttribute( CSS.Attribute.MARGIN_LEFT );
        if ( paddingLeft != null ) {
            final Float padding = parseLength( String.valueOf( paddingLeft ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_LEFT, padding );
        }
        final Object paddingBottom = attr.getAttribute( CSS.Attribute.MARGIN_BOTTOM);
        if ( paddingBottom != null ) {
            final Float padding = parseLength( String.valueOf( paddingBottom ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_BOTTOM, padding );
        }
        final Object paddingRight = attr.getAttribute( CSS.Attribute.MARGIN_RIGHT );
        if ( paddingRight != null ) {
            final Float padding = parseLength( String.valueOf( paddingRight ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.PADDING_RIGHT, padding );
        }
    }

    private void parseSize(Element result, AttributeSet attr) {
        final Object heightText = attr.getAttribute( CSS.Attribute.HEIGHT );
        if ( heightText != null ) {
            result.getStyle().setStyleProperty( ElementStyleKeys.MIN_HEIGHT, parseLength( String.valueOf( heightText ) ) );
        }
        final Object widthText = attr.getAttribute( CSS.Attribute.WIDTH );
        if ( widthText != null ) {
            result.getStyle().setStyleProperty( ElementStyleKeys.MIN_WIDTH, parseLength( String.valueOf( widthText ) ) );
        }
    }

    private void parseBorder(final Element result, final StyleSheet sheet, final AttributeSet attr ) {

        final Object borderStyleText = attr.getAttribute( CSS.Attribute.BORDER_STYLE );
        if ( borderStyleText != null ) {
            final BorderStyle borderStyle = BorderStyle.getBorderStyle( String.valueOf( borderStyleText ) );
            if ( borderStyle != null ) {
                result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_BOTTOM_STYLE, borderStyle );
                result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_TOP_STYLE, borderStyle );
                result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_LEFT_STYLE, borderStyle );
                result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_RIGHT_STYLE, borderStyle );
            }
        }
        final Object borderWidthText = attr.getAttribute( CSS.Attribute.BORDER_WIDTH );
        if ( borderWidthText != null ) {
            final Float borderWidth = parseLength( String.valueOf( borderWidthText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_BOTTOM_WIDTH, borderWidth );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_TOP_WIDTH, borderWidth );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_LEFT_WIDTH, borderWidth );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_RIGHT_WIDTH, borderWidth );
        }

        final Object borderBottomWidthText = attr.getAttribute( CSS.Attribute.BORDER_BOTTOM_WIDTH );
        if ( borderBottomWidthText != null ) {
            final Float borderWidth = parseLength( String.valueOf( borderBottomWidthText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_BOTTOM_WIDTH, borderWidth );
        }

        final Object borderRightWidthText = attr.getAttribute( CSS.Attribute.BORDER_RIGHT_WIDTH );
        if ( borderRightWidthText != null ) {
            final Float borderWidth = parseLength( String.valueOf( borderRightWidthText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_RIGHT_WIDTH, borderWidth );
        }

        final Object borderTopWidthText = attr.getAttribute( CSS.Attribute.BORDER_TOP_WIDTH );
        if ( borderTopWidthText != null ) {
            final Float borderWidth = parseLength( String.valueOf( borderTopWidthText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_TOP_WIDTH, borderWidth );
        }

        final Object borderLeftWidth = attr.getAttribute( CSS.Attribute.BORDER_LEFT_WIDTH );
        if ( borderLeftWidth != null ) {
            final Float borderWidth = parseLength( String.valueOf( borderLeftWidth ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_LEFT_WIDTH, borderWidth );
        }

        final Object borderColorText = attr.getAttribute( CSS.Attribute.BORDER_COLOR );
        if ( borderColorText != null ) {
            final Color borderColor = sheet.stringToColor( String.valueOf( borderColorText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_BOTTOM_COLOR, borderColor );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_TOP_COLOR, borderColor );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_LEFT_COLOR, borderColor );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_RIGHT_COLOR, borderColor );
        }

    }

    private void parseBgAndFgColors(Element result, StyleSheet sheet, AttributeSet attr) {
        final Object backgroundColor = attr.getAttribute( CSS.Attribute.BACKGROUND_COLOR );
        if ( backgroundColor != null ) {
            result.getStyle().setStyleProperty( ElementStyleKeys.BACKGROUND_COLOR,
                    sheet.stringToColor( String.valueOf( backgroundColor ) ) );
        }

        final Object colorText = attr.getAttribute( CSS.Attribute.COLOR );
        if ( colorText != null ) {
            final Color color = sheet.stringToColor( String.valueOf( colorText ) );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_BOTTOM_COLOR, color );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_TOP_COLOR, color );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_LEFT_COLOR, color );
            result.getStyle().setStyleProperty( ElementStyleKeys.BORDER_RIGHT_COLOR, color );
            result.getStyle().setStyleProperty( ElementStyleKeys.PAINT, color );
        }
        //border color is in border's method
    }

    private void parseCssTheRestStyles(Element result, AttributeSet attr) {
        final Object pageBreakText = attr.getAttribute( "page-break" ); //should be instance of Attribute class, not String
        if ( pageBreakText != null ) {
            //TODO complete and tes me!
            result.getStyle().setBooleanStyleProperty(BandStyleKeys.PAGEBREAK_AFTER, true);
        }

    }

    private void parseCssListStyles(Element result, AttributeSet attr) {
        final Object listStyleText = attr.getAttribute(CSS.Attribute.LIST_STYLE);
        if (listStyleText != null) {
            ListStyle listStyleObj = ListStyle.parse(String.valueOf(listStyleText));
            // just simply, assuming list-style === list-style-type
            result.getStyle().setStyleProperty(BandStyleKeys.LIST_STYLE_KEY, listStyleObj);

        }

        final Object listStyleTypeText = attr.getAttribute(CSS.Attribute.LIST_STYLE_TYPE);
        if (listStyleTypeText != null) {
            ListStyle listStyleObj = ListStyle.parse(String.valueOf(listStyleTypeText));
            result.getStyle().setStyleProperty(BandStyleKeys.LIST_STYLE_KEY, listStyleObj);
        }

        final Object listStyleImageText = attr.getAttribute(CSS.Attribute.LIST_STYLE_IMAGE);
        if (listStyleImageText != null) {
            //ListStyle listStyleObj = ListStyle.parse(String.valueOf(listStyleImageText));
            //TODO list-style-image
        }

        final Object listStylePositionText = attr.getAttribute(CSS.Attribute.LIST_STYLE_POSITION);
        if (listStylePositionText != null) {
            //ListStyle listStyleObj = ListStyle.parse(String.valueOf(listStylePositionText));
            //TODO list-style-position
        }
    }


    public Float parseLength( final String value ) {
        if ( value == null ) {
            return null;
        }

        try {
            final StreamTokenizer strtok = new StreamTokenizer( new StringReader( value ) );
            strtok.parseNumbers();
            final int firstToken = strtok.nextToken();
            if ( firstToken != StreamTokenizer.TT_NUMBER ) {
                return null;
            }
            final double nval = strtok.nval;
            final int nextToken = strtok.nextToken();
            if ( nextToken != StreamTokenizer.TT_WORD ) {
                // yeah, this is against the standard, but we are dealing with deadly ugly non-standard documents here
                // maybe we will be able to integrate a real HTML processor at some point.
                return new Float( nval );
            }

            final String unit = strtok.sval;
            if ( "%".equals( unit ) ) {
                return new Float( -nval );
            }
            if ( "cm".equals( unit ) ) { // cm - in - pt
                return new Float( nval * 25.4 / 72 );
            }

            if ( "mm".equals( unit ) ) { // mm - in - pt
                return new Float( nval * 2.54 / 72 );
            }
            if ( "pt".equals( unit ) ) { // base unit
                return new Float( nval );
            }
            if ( "in".equals( unit ) ) {
                return new Float( nval * 72 );
            }
            if ( "px".equals( unit ) ) { // assuming 96dpi: 3pt = 4px
                return new Float( nval * 3.0 / 4.0 );
            }
            if ( "pc".equals( unit ) ) {
                return new Float( nval * 12 );
            }
            return null;
        } catch ( IOException ioe ) {
            return null;
        }
    }

}
