package cucumber.eclipse.editor.editors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import cucumber.eclipse.steps.integration.IStepDefinitions;
import cucumber.eclipse.steps.integration.Step;


public class PopupMenuFindStepActionDelegate implements IEditorActionDelegate {

	private final static String EXTENSION_POINT_STEPDEFINITIONS_ID = "cucumber.eclipse.steps.integration";
	private IEditorPart editorPart;
	private Pattern cukePattern = Pattern.compile("(?:Given|When|Then|And|But) (.*)$");
	private Pattern variablePattern = Pattern.compile("<([^>]+)>");

	private List<IStepDefinitions> getStepDefinitions() {
		List<IStepDefinitions> stepDefs = new ArrayList<IStepDefinitions>();
		IConfigurationElement[] config = Platform
				.getExtensionRegistry()
				.getConfigurationElementsFor(EXTENSION_POINT_STEPDEFINITIONS_ID);
		try {
			for (IConfigurationElement ce : config) {
				Object obj = ce.createExecutableExtension("class");
				if (obj instanceof IStepDefinitions) {
					stepDefs.add((IStepDefinitions) obj);
				}
			}
		} catch (CoreException e) {
		}
		return stepDefs;
	}

	@Override
	public void run(IAction action) {

		IEditorInput input = editorPart.getEditorInput();
		
		// Editor contents needs to be associated with an eclipse project
		// for this to work, if not then simply do nothing.
		if (!(input instanceof IFileEditorInput)) return;
		IProject project = ((IFileEditorInput) input).getFile().getProject();

		Set<Step> steps = new HashSet<Step>();
		for (IStepDefinitions stepDef : getStepDefinitions()) {
			steps.addAll(stepDef.getSteps(project));
		}

		String selectedLine = getSelectedLine();
		
		Step matchedStep = matchSteps(steps, selectedLine);
		try {
			if (matchedStep != null) openEditor(matchedStep);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
	}
	
	Step matchSteps(Set<Step> steps, String currentLine) {
		Matcher matcher = cukePattern.matcher(currentLine);
		if (matcher.matches()) {
			String cukeStep = matcher.group(1);
		
			// FIXME: Replace variables with 0 for now to allow them to match steps
			// Should really read the whole scenario outline and sub in the first scenario
			Matcher variableMatcher = variablePattern.matcher(cukeStep);
			cukeStep = variableMatcher.replaceAll("0");
			
			for(Step step: steps) {
				if (step.matches(cukeStep)) {
					return step;
				}
			}
			
		} 
		return null;
	}
	
	private void openEditor(Step step) throws CoreException {
		   IResource file = step.getSource();
		   IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

		   HashMap<String, Integer> map = new HashMap<String, Integer>();
		   map.put(IMarker.LINE_NUMBER, step.getLineNumber());
		   IMarker marker = file.createMarker(IMarker.TEXT);
		   marker.setAttributes(map);
		   IDE.openEditor(page, marker);
		   marker.delete();
	}
	

	private String getSelectedLine() {
		
		ITextEditor editor = (ITextEditor) editorPart;
				
		TextSelection selecton = (TextSelection) editor.getSelectionProvider().getSelection();
		int line = selecton.getStartLine();	
		
		IDocumentProvider docProvider = editor.getDocumentProvider();
		IDocument doc = docProvider.getDocument(editorPart.getEditorInput());
		try {
			String stepLine = doc.get(doc.getLineOffset(line), doc.getLineLength(line)).trim();
			return stepLine;
		} catch (BadLocationException e) {
			return "";
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
	}

	@Override
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		this.editorPart = targetEditor;
	}

}
