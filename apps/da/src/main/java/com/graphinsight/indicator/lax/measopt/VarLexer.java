// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.measopt;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class VarLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, VAR=4, NUM=5, MEASTEXT=6, ADD=7, SUB=8, MUL=9, 
		MULX=10, DIV=11, DIVX=12, NEWLINE=13, SPACE=14;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "VAR", "NUM", "MEASTEXT", "ADD", "SUB", "MUL", 
			"MULX", "DIV", "DIVX", "NEWLINE", "SPACE"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'('", "')'", null, null, null, "'+'", "'-'", "'*'", "'\\u00D7'", 
			"'/'", "'\\u00F7'", null, "' '"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, "VAR", "NUM", "MEASTEXT", "ADD", "SUB", "MUL", 
			"MULX", "DIV", "DIVX", "NEWLINE", "SPACE"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public VarLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Var.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u000eP\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b"+
		"\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0003\u0004\u0003%\b"+
		"\u0003\u000b\u0003\f\u0003&\u0001\u0004\u0004\u0004*\b\u0004\u000b\u0004"+
		"\f\u0004+\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0004\u00056\b\u0005\u000b\u0005"+
		"\f\u00057\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b"+
		"\u0001\u000b\u0001\f\u0003\fI\b\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0000\u0000\u000e\u0001\u0001\u0003\u0002\u0005\u0003\u0007"+
		"\u0004\t\u0005\u000b\u0006\r\u0007\u000f\b\u0011\t\u0013\n\u0015\u000b"+
		"\u0017\f\u0019\r\u001b\u000e\u0001\u0000\u0002\u0002\u0000AZaz\u0001\u0000"+
		"09S\u0000\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000"+
		"\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000"+
		"\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000"+
		"\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000"+
		"\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000"+
		"\u0015\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000"+
		"\u0019\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0001"+
		"\u001d\u0001\u0000\u0000\u0000\u0003\u001f\u0001\u0000\u0000\u0000\u0005"+
		"!\u0001\u0000\u0000\u0000\u0007$\u0001\u0000\u0000\u0000\t)\u0001\u0000"+
		"\u0000\u0000\u000b-\u0001\u0000\u0000\u0000\r;\u0001\u0000\u0000\u0000"+
		"\u000f=\u0001\u0000\u0000\u0000\u0011?\u0001\u0000\u0000\u0000\u0013A"+
		"\u0001\u0000\u0000\u0000\u0015C\u0001\u0000\u0000\u0000\u0017E\u0001\u0000"+
		"\u0000\u0000\u0019H\u0001\u0000\u0000\u0000\u001bL\u0001\u0000\u0000\u0000"+
		"\u001d\u001e\u0005=\u0000\u0000\u001e\u0002\u0001\u0000\u0000\u0000\u001f"+
		" \u0005(\u0000\u0000 \u0004\u0001\u0000\u0000\u0000!\"\u0005)\u0000\u0000"+
		"\"\u0006\u0001\u0000\u0000\u0000#%\u0007\u0000\u0000\u0000$#\u0001\u0000"+
		"\u0000\u0000%&\u0001\u0000\u0000\u0000&$\u0001\u0000\u0000\u0000&\'\u0001"+
		"\u0000\u0000\u0000\'\b\u0001\u0000\u0000\u0000(*\u0007\u0001\u0000\u0000"+
		")(\u0001\u0000\u0000\u0000*+\u0001\u0000\u0000\u0000+)\u0001\u0000\u0000"+
		"\u0000+,\u0001\u0000\u0000\u0000,\n\u0001\u0000\u0000\u0000-.\u0005[\u0000"+
		"\u0000./\u0005M\u0000\u0000/0\u0005E\u0000\u000001\u0005A\u0000\u0000"+
		"12\u0005S\u0000\u000023\u0005_\u0000\u000035\u0001\u0000\u0000\u00004"+
		"6\u0002az\u000054\u0001\u0000\u0000\u000067\u0001\u0000\u0000\u000075"+
		"\u0001\u0000\u0000\u000078\u0001\u0000\u0000\u000089\u0001\u0000\u0000"+
		"\u00009:\u0005]\u0000\u0000:\f\u0001\u0000\u0000\u0000;<\u0005+\u0000"+
		"\u0000<\u000e\u0001\u0000\u0000\u0000=>\u0005-\u0000\u0000>\u0010\u0001"+
		"\u0000\u0000\u0000?@\u0005*\u0000\u0000@\u0012\u0001\u0000\u0000\u0000"+
		"AB\u0005\u00d7\u0000\u0000B\u0014\u0001\u0000\u0000\u0000CD\u0005/\u0000"+
		"\u0000D\u0016\u0001\u0000\u0000\u0000EF\u0005\u00f7\u0000\u0000F\u0018"+
		"\u0001\u0000\u0000\u0000GI\u0005\r\u0000\u0000HG\u0001\u0000\u0000\u0000"+
		"HI\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000JK\u0005\n\u0000\u0000"+
		"K\u001a\u0001\u0000\u0000\u0000LM\u0005 \u0000\u0000MN\u0001\u0000\u0000"+
		"\u0000NO\u0006\r\u0000\u0000O\u001c\u0001\u0000\u0000\u0000\u0005\u0000"+
		"&+7H\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}