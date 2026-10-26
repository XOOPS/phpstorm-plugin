<{foreach item=story_item from=$story}>
  <h2><{$story_item.title}></h2>
  <{foreach item=story from=$story_item.related}><{$story.title}><{/foreach}>
  <{* <{/foreach}> *}>
  <{$story_item|default:''}> <{$storyline}>
<{foreachelse}>
  <{$story}>
<{/foreach}>
<{foreach item=row from=$rows}><{$row}><{/foreach}>
<{foreach from=$items|default:null key=k item=items_item}><{$items_item}><{/foreach}>
<{foreach item=x from=$x.list}><{$x}><{/foreach}>
