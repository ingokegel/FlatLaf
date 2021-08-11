/*
 * Copyright 2021 FormDev Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.formdev.flatlaf.themeeditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.StringUtils;

/**
 * @author Karl Tauber
 */
class FlatThemePropertiesBaseManager
{
	private static Class<?>[] CORE_THEMES = {
		FlatLaf.class,
		FlatLightLaf.class,
		FlatDarkLaf.class,
		FlatIntelliJLaf.class,
		FlatDarculaLaf.class,
	};

	private final Map<String, MyBasePropertyProvider> providers = new HashMap<>();
	private Map<String, Properties> coreThemes;

	FlatThemePropertiesSupport.BasePropertyProvider create( File file, FlatThemePropertiesSupport propertiesSupport ) {
		String name = StringUtils.removeTrailing( file.getName(), ".properties" );
		boolean isCoreTheme = file.getParent().replace( '\\', '/' ).endsWith( "/com/formdev/flatlaf" );
		MyBasePropertyProvider provider = new MyBasePropertyProvider( name, propertiesSupport, isCoreTheme );
		providers.put( name, provider );
		return provider;
	}

	void clear() {
		providers.clear();
	}

	private void loadCoreThemes() {
		if( coreThemes != null )
			return;

		coreThemes = new HashMap<>();

		for( Class<?> lafClass : CORE_THEMES ) {
			String propertiesName = '/' + lafClass.getName().replace( '.', '/' ) + ".properties";
			try( InputStream in = lafClass.getResourceAsStream( propertiesName ) ) {
				Properties properties = new Properties();
				if( in != null )
					properties.load( in );
				coreThemes.put( lafClass.getSimpleName(), properties );
			} catch( IOException ex ) {
				ex.printStackTrace();
			}
		}
	}

	private static List<String> baseFiles( String name, String baseTheme ) {
		ArrayList<String> result = new ArrayList<>();

		// core themes
		switch( name ) {
			case "FlatLaf":
				result.add( "FlatLightLaf" );
				break;

			case "FlatLightLaf":
			case "FlatDarkLaf":
				result.add( "FlatLaf" );
				break;

			case "FlatIntelliJLaf":
				result.add( "FlatLightLaf" );
				result.add( "FlatLaf" );
				break;

			case "FlatDarculaLaf":
				result.add( "FlatDarkLaf" );
				result.add( "FlatLaf" );
				break;
		}

		// custom themes based on core themes
		if( result.isEmpty() ) {
			if( baseTheme == null )
				baseTheme = "light";

			switch( baseTheme ) {
				default:
				case "light":
					result.add( "FlatLightLaf" );
					result.add( "FlatLaf" );
					break;

				case "dark":
					result.add( "FlatDarkLaf" );
					result.add( "FlatLaf" );
					break;

				case "intellij":
					result.add( "FlatIntelliJLaf" );
					result.add( "FlatLightLaf" );
					result.add( "FlatLaf" );
					break;

				case "darcula":
					result.add( "FlatDarculaLaf" );
					result.add( "FlatLightLaf" );
					result.add( "FlatLaf" );
					break;
			}
		}

		return result;
	}

	//---- class MyBasePropertyProvider ---------------------------------------

	private class MyBasePropertyProvider
		implements FlatThemePropertiesSupport.BasePropertyProvider
	{
		private final String name;
		private final FlatThemePropertiesSupport propertiesSupport;
		private final boolean isCoreTheme;

		private List<String> baseFiles;
		private String lastBaseTheme;

		MyBasePropertyProvider( String name, FlatThemePropertiesSupport propertiesSupport, boolean isCoreTheme ) {
			this.name = name;
			this.propertiesSupport = propertiesSupport;
			this.isCoreTheme = isCoreTheme;
		}

		@Override
		public String getProperty( String key, String baseTheme ) {
			updateBaseFiles( baseTheme );

			// search in opened editors
			for( String baseFile : baseFiles ) {
				String value = getPropertyFromBase( baseFile, key );
				if( value != null )
					return value;
			}

			// search in core themes
			if( !isCoreTheme ) {
				loadCoreThemes();

				String value = getPropertyFromCore( name, key );
				if( value != null )
					return value;

				for( String baseFile : baseFiles ) {
					value = getPropertyFromCore( baseFile, key );
					if( value != null )
						return value;
				}
			}

			return null;
		}

		private String getPropertyFromBase( String baseFile, String key ) {
			MyBasePropertyProvider provider = providers.get( baseFile );
			return (provider != null)
				? provider.propertiesSupport.getProperties().getProperty( key )
				: null;
		}

		private String getPropertyFromCore( String baseFile, String key ) {
			Properties properties = coreThemes.get( baseFile );
			return (properties != null)
				? properties.getProperty( key )
				: null;
		}

		private void updateBaseFiles( String baseTheme ) {
			if( baseFiles != null && Objects.equals( baseTheme, lastBaseTheme ) )
				return;

			baseFiles = baseFiles( name, baseTheme );
			lastBaseTheme = baseTheme;
		}

		@Override
		public void addAllKeys( Set<String> allKeys, String baseTheme ) {
			updateBaseFiles( baseTheme );

			// search in opened editors
			for( String baseFile : baseFiles ) {
				MyBasePropertyProvider provider = providers.get( baseFile );
				if( provider == null )
					continue;

				for( Object key : provider.propertiesSupport.getProperties().keySet() )
					allKeys.add( (String) key );
			}

			// search in core themes
			if( !isCoreTheme ) {
				loadCoreThemes();

				copyKeys( coreThemes.get( name ), allKeys );

				for( String baseFile : baseFiles )
					copyKeys( coreThemes.get( baseFile ), allKeys );
			}
		}

		private void copyKeys( Properties properties, Set<String> allKeys ) {
			if( properties == null )
				return;

			for( Object key : properties.keySet() )
				allKeys.add( (String) key );
		}
	}
}
