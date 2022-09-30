# _plugins_dir="/build-out" 
# _plugin_name="graph-data-science" 
# _destination="${_plugins_dir}/${_plugin_name}.jar" 
# _plugin_jar_url="https://graphdatascience.ninja/neo4j-graph-data-science-2.1.9.jar" 
# echo "Installing Plugin '${_plugin_name}' from ${_plugin_jar_url} to ${_destination} " 
# wget -q --timeout 300 --tries 30 --output-document="${_destination}" "${_plugin_jar_url}"


_plugins_dir="/build-out" 
_plugin_name="apoc" 
_destination="${_plugins_dir}/${_plugin_name}.jar" 
_plugin_jar_url="https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/4.4.0.8/apoc-4.4.0.8-all.jar" 
echo "Installing Plugin '${_plugin_name}' from ${_plugin_jar_url} to ${_destination} " 
wget -q --timeout 300 --tries 30 --output-document="${_destination}" "${_plugin_jar_url}"