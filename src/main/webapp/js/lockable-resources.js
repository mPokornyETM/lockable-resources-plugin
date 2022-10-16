// SPDX-License-Identifier: MIT
// Copyright (c) 2020, Tobias Gruetzmacher

var resources = {};
var permissions = {};

function find_resource_name() {
  var table = document.getElementById('lockable-resources');
  var resources = [];
  for (i = 1; i < table.rows.length; i++) {
    var checkBox = table.rows.item(i).cells[0];

    if (checkBox.firstElementChild.childNodes[0].checked) {
      var resourceName = table.rows.item(i).dataset.resourceName;
      resources.push(resourceName);
    }
  }

  return resources;
}

function resource_action(button, action) {

  var selectedResources = find_resource_name();
  console.log('selectedResources ', selectedResources);

  if (selectedResources === undefined || selectedResources.size() <= 0) {
    return;
  }

  var form = document.createElement('form');
  form.setAttribute('method', 'POST');
  if (selectedResources.size() == 1) {
    form.setAttribute('action', action + "?resource=" + encodeURIComponent(selectedResources[0]));
  } else {
    form.setAttribute('action', action + "?resources=" + encodeURIComponent(selectedResources.join('\n')));
  }
  crumb.appendToForm(form);
  document.body.appendChild(form);
  form.submit();
}

function updateActionsBar(checkBox, resourceData) {

  disableUnPermittedButtons(false);

  if (checkBox.checked && updateActionsBarButtons(resourceData)) {
    return;
  }

  var resourceName = resourceData.resourceName;
  console.log('updateActionsBar:resourceName', resourceName, checkBox);

  resources[resourceName] = resourceData;

  var table = document.getElementById('lockable-resources');
  var selectedCount = 0;
  for (i = 1; i < table.rows.length; i++) {

    var checkBox = table.rows.item(i).cells[0];

    if (checkBox.firstElementChild.childNodes[0].checked) {
      var resourceNameToCheck = table.rows.item(i).dataset.resourceName;
      var resource = resources[resourceNameToCheck];
      if (resource !== undefined) {
        selectedCount++;
        if (updateActionsBarButtons(resource)) {
          return;
        }
      }
    }
  }

  // disable all buttons in case nothing is selected
  if (selectedCount <= 0) {
    document.getElementById('resource_action_unlock').disabled = true;
    document.getElementById('resource_action_steal').disabled = true;
    document.getElementById('resource_action_reserve').disabled = true;
    document.getElementById('resource_action_unreserve').disabled = true;
    document.getElementById('resource_action_reassign').disabled = true;
    document.getElementById('resource_action_reset').disabled = true;
    document.getElementById('resource_action_edit').disabled = true;
  }
}

function initActionsBarButtons(_permissions) {


  console.log('initActionsBarButtons', _permissions, permissions);

  onInit = true;
  permissions = {
    UNLOCK: _permissions.UNLOCK || _permissions.ADMINISTER,
    // Should this also be permitted by "STEAL"?..
    RESET: _permissions.UNLOCK || _permissions.ADMINISTER,
    STEAL: _permissions.STEAL || _permissions.ADMINISTER,
    REASSIGN: _permissions.STEAL || _permissions.ADMINISTER,
    RESERVE: _permissions.RESERVE || _permissions.ADMINISTER,
    UNRESERVE: _permissions.RESERVE || permissions.ADMINISTER,
    EDIT: _permissions.RESERVE || _permissions.UNLOCK || _permissions.STEAL || _permissions.ADMINISTER,
    ADMINISTER: _permissions.ADMINISTER
  }

  // unselect old selections
  var table = document.getElementById('lockable-resources');
  for (i = 1; i < table.rows.length; i++) {
    var resourceName = table.rows.item(i).dataset.resourceName;
    var checkBox = table.rows.item(i).cells[0];
    checkBox.firstElementChild.childNodes[0].checked = false;


    if (!canBeEdited()) {
      document.getElementById('note-btn-' + resourceName).style.display = "none";
    }
  }

  // enable only permitted operations
  disableUnPermittedButtons(true);

  // hide un-permitted operations
  hideUnPermittedButtons();
}

function hideUnPermittedButtons() {

  Object.keys(permissions).forEach((key) => {
    console.log('hideUnPermittedButtons', key, hasPermission(key));
    if (!hasPermission(key)) {
      var el = document.getElementById('resource_action_' + key.toLowerCase());
      if (el)
        el.style.display = "none";
      else
        console.log('hideUnPermittedButtons', key, 'Element does not exist', 'resource_action_' + key.toLowerCase());
    }
  });

}

function disableUnPermittedButtons(disable) {

  Object.keys(permissions).forEach((key) => {
    console.log('disableUnPermittedButtons', key, hasPermission(key));
    var el = document.getElementById('resource_action_' + key.toLowerCase());
    if (el) {
      if (disable)
        el.disabled = hasPermission(key);
      else
        el.disabled = !hasPermission(key);
    }
    else
      console.log('disableUnPermittedButtons', key, 'Element does not exist', 'resource_action_' + key.toLowerCase());

  });

}

function updateActionsBarButtons(resource) {
  console.log('updateActionsBarButtons', resource)
  if (!canBeUnlocked(resource))
    document.getElementById('resource_action_unlock').disabled = true;

  if (!canBeStolen(resource))
    document.getElementById('resource_action_steal').disabled = true;

  if (!canBeReserved(resource))
    document.getElementById('resource_action_reserve').disabled = true;

  if (!canBeUnreserved(resource))
    document.getElementById('resource_action_unreserve').disabled = true;

  if (!canBeReassign(resource))
    document.getElementById('resource_action_reassign').disabled = true;

  if (!canBeReset(resource))
    document.getElementById('resource_action_reset').disabled = true;

  return document.getElementById('resource_action_unlock').disabled &&
    document.getElementById('resource_action_steal').disabled &&
    document.getElementById('resource_action_unreserve').disabled &&
    document.getElementById('resource_action_reassign').disabled &&
    document.getElementById('resource_action_reset').disabled &&
    document.getElementById('resource_action_reserve').disabled;
}

function hasPermission(level) {
  return permissions[level];
}

function canBeUnlocked(resource) {
  return (resource.isLocked || resource.isReserved) && hasPermission('UNLOCK');
}

function canBeStolen(resource) {
  return !resource.isEphemeral && resource.isLocked && hasPermission('STEAL');
}

function canBeReserved(resource) {
  if (resource.isLocked || resource.isReserved || resource.isQueued) {
    return false;
  }
  return hasPermission('RESERVE');
}

function canBeUnreserved(resource) {
  if (hasPermission('ADMINISTER')) {
    return resource.isReserved;
  }
  return resource.isReservedByCurrentUser && hasPermission('UNRESERVE');
}

function canBeReassign(resource) {
  if (resource.isReserved) {
    if (hasPermission('ADMINISTER')) {
      return true;
    }
    return resource.isReservedByCurrentUser && hasPermission('UNRESERVE') && hasPermission('REASSIGN');
  }
  else if (resource.isLocked) {
    //@todo check somehow if the user has permission for this build
    return hasPermission('ADMINISTER');
  }
  return false;
}

function canBeEdited() {
  return hasPermission('EDIT');
}

function canBeReset(resource) {
  return hasPermission('RESET') && (canBeUnlocked(resource) || canBeUnreserved(resource) || resource.isQueued);
}

function editNote(button, resource) {
  var resourceName = resource.resourceName;
  var d = document.getElementById("note-" + resourceName);
  $(d).down().next().innerHTML = "<div class='spinner-right' style='flex-grow: 1;'>loading...</div>";
  new Ajax.Request(
    "noteForm",
    {
      parameters: { resource: resourceName },
      onComplete: function (x) {
        d.innerHTML = x.responseText;
        evalInnerHtmlScripts(x.responseText, function () {
          Behaviour.applySubtree(d);
          d.getElementsByTagName("TEXTAREA")[0].focus();
        });
        layoutUpdateCallback.call();
      }
    }
  );
}
